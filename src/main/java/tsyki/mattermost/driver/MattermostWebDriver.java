package tsyki.mattermost.driver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsyki.util.JsonBuilder;

/**
 * MattermostのAPIを叩くクラス<BR>
 * 以下のJSのドライバ互換<BR>
 * https://github.com/mattermost/mattermost-driver-javascript/blob/master/client.jsx
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/07
 */
public class MattermostWebDriver {

    /** ログイン時のログインIDポスト用キー */
    private static final String KEY_LOGIN_ID = "login_id";

    /** ログイン時のパスワードポスト用キー */
    private static final String KEY_PASSWORD = "password";

    /** ログイン結果のトークンが格納されているヘッダキー */
    private static final String KEY_HEADER_TOKEN = "Token";

    /** mattermostのバージョンが格納されているヘッダキー */
    private static final String KEY_MATTERMOST_VERSION = "X-Version-Id";

    /** 送受信で使う文字コード */
    private static final String ENCODING = "UTF-8";

    private Logger logger = Logger.getLogger( this.getClass().getName());

    private CloseableHttpClient httpclient;

    /** mattermostのホスト名。例：http://yourdomain.com。最後に/は付けない */
    private String url;

    /** mattermostのバージョン。3.5.0.3.5.1.5be7bb983a7b7b8d5949dffc6cccad6eのような文字列 */
    private String mattermostVersion;

    /** APIのバージョン */
    private String urlVersion;

    /** 操作するチームのID */
    private String teamId;

    /** ログイン時に生成される認証用トークン */
    private String authToken;

    public MattermostWebDriver() {
        this.httpclient = HttpClients.createDefault();
        this.urlVersion = "/api/v3";
    }

    /**
     * mattermostのURLを返す。<BR>
     * 例：http://yourdomain.com。最後に/は付かない
     * @return
     */
    public String getUrl() {
        return url;
    }

    /**
     * mattermostのホスト名を設定する。<BR>
     * 例：http://yourdomain.com。最後に/は付けない
     */
    public void setUrl( String url) {
        this.url = url;
    }

    /**
     * コネクションを閉じ、利用を完了する。
     * @throws IOException
     */
    public void close() throws IOException {
        httpclient.close();
    }

    /**
     * 利用するチームを設定する。<BR>
     * チームに依存したAPIを利用する場合は事前にこのメソッドを呼ぶ必要がある
     * @param teanName チームの名称(表示名ではなくURLに現れるもの)
     * @throws IOException
     * @throws ClientProtocolException
     */
    public void setTeamIdByName( String findTeamName) throws ClientProtocolException, IOException {
        // NOTE find_team_by_nameというAPIがあるが、これはチャンネルの有無を返すだけで使えなかった。なのでallで取ってから名前で突き合わせる
        HttpGet request = createGetRequest( getAllTeamsPath());

        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String body = getBodyValue( response);
            Map<String, Map<String, String>> result = parseJsonToMap( body);
            for ( Map<String, String> teamMap : result.values()) {
                String teamName = teamMap.get( "name");
                if ( findTeamName.equals( teamName)) {
                    this.teamId = teamMap.get( "id");
                    break;
                }
            }
        }

    }

    /**
     * 指定のnameを持つチャンネルのIDを返す
     * @param findChannelName
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public String getChannelIdByName( String findChannelName) throws ClientProtocolException, IOException {
        // XXX nameからチャンネルを取得する専用のAPIがあるのかもしれないが、見つからなかったので全部取ってきて探す。イマイチ。
        List<Channel> channels = getAllChannels();
        for ( Channel channel : channels) {
            if ( findChannelName.equals( channel.getName())) {
                return channel.getId();
            }
        }
        return null;
    }

    /**
     * 指定のチャンネルにポストします
     * @param postChannelId
     * @param msg
     * @throws IOException
     * @throws ClientProtocolException
     */
    public void post( String channelId, String msg) throws ClientProtocolException, IOException {

        String strJson = JsonBuilder.builder()//
            .put( "message", msg)//
            .put( "channel_id", channelId)//
            .build();
        HttpPost request = createPostRequest( getPostCreatePath( channelId), strJson);
        addAuthHeader( request);

        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String body = getBodyValue( response);
            logger.fine( body.toString());
        }
    }

    private static boolean isEmpty( String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Incoming Webhookを使って投稿します
     * @param webhookUrl Incoming Webhookで表示されるURL
     * @param msg 投稿する文面
     * @param postChannelName 投稿するチャンネル。空ならばIncoming Webhookで指定したチャンネルが使われる
     * @param postUserName 投稿するユーザ名。空ならばデフォルト(Webhook)となる
     * @param postIconUrl 投稿するユーザのアイコン。空ならばWebhookのデフォルトのものとなる
     */
    public void postIncomingWebhook( String webhookUrl, String msg, String postChannelName, String postUserName, String postIconUrl)
            throws ClientProtocolException, IOException {

        JsonBuilder jsonBuilder = JsonBuilder.builder();
        jsonBuilder.put( "text", msg);
        if ( !isEmpty( postChannelName)) {
            jsonBuilder.put( "channel", postChannelName);
        }
        if ( !isEmpty( postUserName)) {
            jsonBuilder.put( "username", postUserName);
        }
        if ( !isEmpty( postIconUrl)) {
            jsonBuilder.put( "icon_url", postIconUrl);
        }
        HttpPost request = createPostRequest( webhookUrl, jsonBuilder.build());

        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String body = getBodyValue( response);
            logger.fine( body.toString());
        }
    }

    /**
     * 指定のチャンネルに所属します
     * @param channelId
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void joinChannel( String channelId) throws ClientProtocolException, IOException {
        HttpPost request = createPostRequest( getJoinChannelPath( channelId), "");
        addAuthHeader( request);
        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String body = getBodyValue( response);
            logger.fine( body.toString());
        }
    }

    private <T> Map<String, T> parseJsonToMap( String json) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings( "unchecked")
        Map<String, T> result = mapper.readValue( json, Map.class);
        return result;
    }

    private <T> List<T> parseJsonToList( String json) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings( "unchecked")
        List<T> result = ( List<T>) mapper.readValue( json, List.class);
        return result;
    }

    /**
     * ログインユーザが参照可能な全てのチャンネルを返します(参加していないチャンネルも含む)
     * @return
     * @throws IOException
     * @throws ClientProtocolException
     */
    public List<Channel> getAllChannels() throws ClientProtocolException, IOException {
        List<Channel> channels = new LinkedList<Channel>();
        channels.addAll( getJoinedChannels());
        channels.addAll( getMoreChannels());
        return channels;
    }

    /**
     * ログインユーザが参照可能な全ての公開チャンネルを返します(参加していないチャンネルも含む)
     * @return
     * @throws IOException
     * @throws ClientProtocolException
     */
    public List<Channel> getAllPublicChannels() throws ClientProtocolException, IOException {
        List<Channel> channels = getAllChannels();
        // DirectMessage、非公開チャンネルは除外
        return channels.stream().filter( c -> c.isPublicChannel()).collect( Collectors.toList());
    }

    /**
     * ログインユーザが参加しているチャンネル一覧を返します。ダイレクトメッセージ、非公開チャンネルも含まれます
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public List<Channel> getJoinedChannels() throws ClientProtocolException, IOException {
        return getChannels( getJoinedChannelPath());
    }

    /**
     * ログインユーザが参加していないチャンネル一覧を返します
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public List<Channel> getMoreChannels() throws ClientProtocolException, IOException {
        return getChannels( getMoreChannelPath());
    }

    private List<Channel> getChannels( String path) throws ClientProtocolException, IOException {
        HttpGet request = createGetRequest( path);
        addAuthHeader( request);
        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String body = getBodyValue( response);
            List<Map<String, Object>> channelsMap = parseChannelJson( body);
            List<Channel> channels = new LinkedList<Channel>();
            for ( Map<String, Object> channelMap : channelsMap) {
                Channel channel = parseChannel( channelMap);
                channels.add( channel);
            }
            return channels;
        }
    }

    // getChannelの結果をmattermostのバージョンに応じてparse
    private List<Map<String, Object>> parseChannelJson( String body) throws JsonParseException, JsonMappingException, IOException {
        // 3.4以前のもの
        if ( isLessThanOrEqualVersion( new BigDecimal( "3.4"))) {
            return parseChannelJson_3_4( body);
        }
        else {
            return parseChannelJson_3_5( body);
        }
    }

    /**
     * mattermostのバージョンが引数以下かどうかを判定する
     * @param version 3.5のような値
     * @return
     */
    private boolean isLessThanOrEqualVersion( BigDecimal version) {
        if ( mattermostVersion == null) {
            throw new NullPointerException( "mattermostVersion is not initialized.");
        }
        String[] dividedVersionStrs = mattermostVersion.split( "\\.");
        // 3.5のような文字列を作る
        String versionNumStr = dividedVersionStrs[0] + "." + dividedVersionStrs[1];
        BigDecimal mattermostVersionNum = new BigDecimal( versionNumStr);
        return mattermostVersionNum.compareTo( version) <= 0;
    }

    // mattermost3.4のgetChannelの結果をパース
    private List<Map<String, Object>> parseChannelJson_3_4( String body) throws JsonParseException, JsonMappingException, IOException {
        Map<String, List<Map<String, Object>>> allMap = parseJsonToMap( body);
        List<Map<String, Object>> channelsMap = allMap.get( "channels");
        return channelsMap;
    }

    // mattermost3.5以降のgetChannelの結果をパース
    private List<Map<String, Object>> parseChannelJson_3_5( String body) throws JsonParseException, JsonMappingException, IOException {
        List<Map<String, Object>> channelsMap = parseJsonToList( body);
        return channelsMap;
    }

    // Jsonから作成されたチャンネル情報を元にChannelを作成
    private Channel parseChannel( Map<String, Object> channelMap) {
        Channel channel = new Channel();
        channel.setId( ( String) channelMap.get( "id"));
        channel.setName( ( String) channelMap.get( "name"));
        channel.setDisplayName( ( String) channelMap.get( "display_name"));
        channel.setType( ( String) channelMap.get( "type"));
        Date createAt = new Date( ( Long) channelMap.get( "create_at"));
        channel.setCreateAt( createAt);
        // XXX 作成直後はcreate_atと同じ値になっているのだろうか？
        Date updateAt = new Date( ( Long) channelMap.get( "update_at"));
        channel.setUpdateAt( updateAt);
        return channel;
    }

    private void addAuthHeader( HttpRequestBase request) {
        if ( authToken == null) {
            throw new IllegalStateException( "not logged in.");
        }
        request.addHeader( "Authorization", "Bearer " + authToken);
    }

    public String getTeamId() {
        return teamId;
    }

    private HttpPost createPostRequest( String postPath, String postJson) throws UnsupportedEncodingException {
        HttpPost request = new HttpPost( postPath);

        StringEntity body = new StringEntity( postJson, ENCODING);
        request.addHeader( "Content-type", "application/json");
        request.setEntity( body);

        return request;
    }

    private HttpGet createGetRequest( String getPath) throws UnsupportedEncodingException {
        HttpGet request = new HttpGet( getPath);

        request.addHeader( "Content-type", "application/json");

        return request;
    }

    /**
     * ログインを行い認証用トークンを保持します
     * @param loginId
     * @param password
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void login( String loginId, String password) throws ClientProtocolException, IOException {
        String strJson = JsonBuilder.builder().put( KEY_LOGIN_ID, loginId).put( KEY_PASSWORD, password).build();
        HttpPost request = createPostRequest( getLoginPath(), strJson);

        try (CloseableHttpResponse response = httpclient.execute( request)) {
            String authToken;
            try {
                authToken = getHeaderValue( response, KEY_HEADER_TOKEN);
                this.mattermostVersion = getHeaderValue( response, KEY_MATTERMOST_VERSION);
            }
            catch ( Exception e) {
                // TODO アドレスが不正とか、ユーザ名、パスワードが不正とかちゃんと区別する
                throw new IllegalStateException( "認証用トークンが取得できません。チーム名、ユーザ名、パスワードが正しいか確認してください。response=" + response);
            }

            HttpEntity entity = response.getEntity();
            EntityUtils.consume( entity);

            this.authToken = authToken;
        }
    }

    private String getHeaderValue( CloseableHttpResponse response, String headerName) {
        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException( response.getStatusLine().toString());
        }

        Header[] tokenHeaders = response.getHeaders( headerName);
        if ( tokenHeaders == null || tokenHeaders.length == 0) {
            throw new IllegalStateException( headerName + " is not exists in header.");
        }
        return tokenHeaders[0].getValue();
    }

    private String getBodyValue( CloseableHttpResponse response) throws IOException {
        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException( response.toString());
        }
        HttpEntity entity = response.getEntity();
        String result = EntityUtils.toString( entity, ENCODING);
        EntityUtils.consume( response.getEntity());
        return result;
    }

    // 以下URL系
    private String getBaseRoute() {
        if ( url == null) {
            throw new IllegalStateException( "url is not set");
        }
        return getUrl() + urlVersion;
    }

    private String getUsersRoute() {
        return getBaseRoute() + "/users";
    }

    private String getTeamsRoute() {
        return getBaseRoute() + "/teams";
    }

    private String getTeamNeededRoute() {
        if ( teamId == null) {
            throw new IllegalStateException( "teamId is not set");
        }
        return getTeamsRoute() + "/" + getTeamId();
    }

    private String getChannelsRoute() {
        return getTeamNeededRoute() + "/channels";
    }

    private String getChannelNeededRoute( String channelId) {
        return getChannelsRoute() + "/" + channelId;
    }

    private String getPostsRoute( String channelId) {
        return getChannelNeededRoute( channelId) + "/posts";
    }

    private String getLoginPath() {
        return getUsersRoute() + "/login";
    }

    private String getAllTeamsPath() {
        return getTeamsRoute() + "/all";
    }

    private String getJoinedChannelPath() {
        return getChannelsRoute() + "/";
    }

    private String getMoreChannelPath() {
        return getChannelsRoute() + "/more";
    }

    private String getJoinChannelPath( String channelId) {
        return getChannelNeededRoute( channelId) + "/join";
    }

    private String getPostCreatePath( String channelId) {
        return getPostsRoute( channelId) + "/create";
    }
}
