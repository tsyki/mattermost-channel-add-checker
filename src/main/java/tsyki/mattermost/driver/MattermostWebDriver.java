package tsyki.mattermost.driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

import tsyki.util.JsonBuilder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private CloseableHttpClient httpclient;

    /** mattermostのホスト名。例：http://yourdomain.com。最後に/は付けない */
    private String url;

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
     * 利用するチームを設定する。<BR>
     * チームに依存したAPIを利用する場合は事前にこのメソッドを呼ぶ必要がある
     * @param teanName
     * @throws IOException
     * @throws ClientProtocolException
     */
    public void setTeamIdByName( String findTeamName) throws ClientProtocolException, IOException {
        // XXX find_team_by_nameがうまく動かなかったのでallで取ってから名前で突き合わせる
        HttpGet request = createGetRequest( getAllTeamsPath());

        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute( request);
            List<String> bodyLines = getBodyValue( response);
            // NOTE 結果は一行しかない
            for ( String line : bodyLines) {
                Map<String, Map<String, String>> result = parseJson( line);
                for ( Map<String, String> teamMap : result.values()) {
                    String teamName = teamMap.get( "display_name");
                    if ( findTeamName.equals( teamName)) {
                        this.teamId = teamMap.get( "id");
                        break;
                    }
                }
            }
            // XXX これ要る？
            EntityUtils.consume( response.getEntity());
        }
        finally {
            if ( response != null) {
                response.close();
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

        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute( request);
            List<String> bodyLines = getBodyValue( response);
            System.out.println( bodyLines);
            // XXX これ要る？
            EntityUtils.consume( response.getEntity());
        }
        finally {
            if ( response != null) {
                response.close();
            }
        }
    }

    private <T> Map<String, T> parseJson( String json) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings( "unchecked")
        Map<String, T> result = mapper.readValue( json, Map.class);
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
     * ログインユーザが参加しているチャンネル一覧を返します
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
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute( request);
            List<String> allLines = getBodyValue( response);
            // NOTE 結果は一行しかない
            for ( String line : allLines) {
                Map<String, List<Map<String, Object>>> allMap = parseJson( line);
                List<Map<String, Object>> channelsMap = allMap.get( "channels");
                List<Channel> channels = new LinkedList<Channel>();
                for ( Map<String, Object> channelMap : channelsMap) {
                    Channel channel = parseChannel( channelMap);
                    channels.add( channel);
                }
                return channels;
            }

            // XXX これ要る？
            EntityUtils.consume( response.getEntity());
        }
        finally {
            if ( response != null) {
                response.close();
            }
        }
        // ここには来ないはず？チャンネルが一つもない場合も本当にそうかは未検証
        throw new IllegalStateException();
    }

    // Jsonから作成されたチャンネル情報を元にChannelを作成
    private Channel parseChannel( Map<String, Object> channelMap) {
        Channel channel = new Channel();
        channel.setId( ( String) channelMap.get( "id"));
        channel.setName( ( String) channelMap.get( "name"));
        channel.setDisplayName( ( String) channelMap.get( "display_name"));
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

        StringEntity body = new StringEntity( postJson, "UTF-8");
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

        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute( request);
            String authToken;
            try {
                authToken = getHeaderValue( response, KEY_HEADER_TOKEN);
            }
            catch ( Exception e) {
                // TODO アドレスが不正とか、ユーザ名、パスワードが不正とかちゃんと区別する
                throw new IllegalStateException( "認証用トークンが取得できません。チーム名、ユーザ名、パスワードが正しいか確認してください。response=" + response);
            }

            // XXX これ必要？
            HttpEntity entity = response.getEntity();
            EntityUtils.consume( entity);

            this.authToken = authToken;
        }
        finally {
            if ( response != null) {
                response.close();
            }
        }
    }

    private String getHeaderValue( CloseableHttpResponse response, String headerName) {
        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException( response.getStatusLine().toString());
        }

        Header[] tokenHeaders = response.getHeaders( KEY_HEADER_TOKEN);
        if ( tokenHeaders == null || tokenHeaders.length == 0) {
            throw new IllegalStateException( headerName + " is not exists in header.");
        }
        return tokenHeaders[0].getValue();
    }

    private List<String> getBodyValue( CloseableHttpResponse response) throws IOException {
        List<String> lines = new LinkedList<String>();
        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException( response.toString());
        }
        HttpEntity entity = response.getEntity();
        InputStream content = entity.getContent();
        BufferedReader br = new BufferedReader( new InputStreamReader( content));
        String line;
        while ( (line = br.readLine()) != null) {
            lines.add( line);
        }
        return lines;
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

    private String getPostsRoute( String channelId) {
        return getChannelsRoute() + "/" + channelId + "/posts";
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

    private String getPostCreatePath( String channelId) {
        return getPostsRoute( channelId) + "/create";
    }

}
