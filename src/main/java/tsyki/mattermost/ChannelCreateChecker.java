package tsyki.mattermost;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import tsyki.util.JsonBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mattermostにチャンネルが追加された場合にその情報を特定チャンネルにポストします
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/01
 */
public class ChannelCreateChecker {

    private static final String KEY_MATTERMOST_URL = "mattermost_url";

    private static final String KEY_TEAM_NAME = "team_name";

    private static final String KEY_LOGIN_ID = "login_id";

    private static final String KEY_PASSWORD = "password";

    private static final String KEY_POST_CANNEL = "post_channel_name";

    private static final String KEY_HEADER_TOKEN = "Token";

    // TODO mattermostのAPI操作は別クラスに切り出し
    private static final String API_HOME_PATH = "/api/v3";

    private static final String LOGIN_PATH = "/users/login";

    private static final String CHANNEL_LIST_PATH = "/channels";

    private static final String LOGIN_JSON_FILE_PATH = "login.json";

    private String mattermostUrl;

    private String postChannelName;

    private String teamName;

    private String loginId;

    private String password;

    private String teamId;

    private String getLoginPath() {
        return mattermostUrl + API_HOME_PATH + LOGIN_PATH;
    }

    private String getAllTeamPath() {
        return mattermostUrl + API_HOME_PATH + "/teams/all";
    }

    private String getTeamIdByNamePath() {
        return mattermostUrl + API_HOME_PATH + "/teams/find_team_by_name";
    }

    private String getChannelListPath() {
        return mattermostUrl + API_HOME_PATH + "/teams/" + teamId + "/channels/";
    }

    public static void main( String[] args) throws IOException {
        String confFilePath = "channel_checker.properties";
        if ( args.length >= 1) {
            confFilePath = args[0];
        }

        ChannelCreateChecker channelChecker = new ChannelCreateChecker( confFilePath);
        channelChecker.run();
    }

    private Properties prop = new Properties();

    public ChannelCreateChecker( String confFilePath) throws IOException {
        loadConfig( confFilePath);
    }

    private void loadConfig( String confFilePath) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream( confFilePath);
            prop.load( is);
            is.close();
        }
        finally {
            if ( is != null) {
                is.close();
            }
        }
        mattermostUrl = prop.getProperty( KEY_MATTERMOST_URL);
        if ( mattermostUrl == null || mattermostUrl.isEmpty()) {
            throw new IllegalStateException( "mattermostのURLがコンフィグファイルに書かれていません");
        }
        teamName = prop.getProperty( KEY_TEAM_NAME);
        loginId = prop.getProperty( KEY_LOGIN_ID);
        password = prop.getProperty( KEY_PASSWORD);
        postChannelName = prop.getProperty( KEY_POST_CANNEL, "timeline");
    }

    public void run() throws IOException, ClientProtocolException, UnsupportedEncodingException {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        String authToken = getAuthToken( httpclient);
        System.out.println( authToken);

        String teamId = getTeamId( httpclient, teamName);
        this.teamId = teamId;

        HttpGet request = new HttpGet( getChannelListPath());
        request.addHeader( "Authorization", "Bearer " + authToken);
        request.addHeader( "Content-type", "application/json");

        CloseableHttpResponse response = httpclient.execute( request);
        System.out.println( response);

        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException();
        }
        HttpEntity entity = response.getEntity();
        InputStream content = entity.getContent();
        BufferedReader br = new BufferedReader( new InputStreamReader( content));
        String line;
        while ( (line = br.readLine()) != null) {
            System.out.println( line);
        }
        EntityUtils.consume( entity);
        // TODO ちゃんとfinallyでやる
        response.close();

    }

    private String getTeamId( CloseableHttpClient httpclient, String authToken, String teamName) throws ClientProtocolException, IOException {
        HttpPost request = new HttpPost( getTeamIdByNamePath());
        request.addHeader( "Authorization", "Bearer " + authToken);
        request.addHeader( "Content-type", "application/json");
        String jsonString = JsonBuilder.builder().put( "name", teamName).build();

        // TODO リファクタ。StringEntityはどっかのクラスに押し込める
        StringEntity body = new StringEntity( jsonString);
        request.setEntity( body);

        CloseableHttpResponse response = httpclient.execute( request);
        System.out.println( response);

        if ( response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException( teamName + " is not exists.");
        }
        HttpEntity entity = response.getEntity();
        InputStream content = entity.getContent();
        BufferedReader br = new BufferedReader( new InputStreamReader( content));
        String line;
        while ( (line = br.readLine()) != null) {
            System.out.println( line);
        }
        EntityUtils.consume( entity);
        // TODO ちゃんとfinallyでやる
        response.close();

        return null;
    }

    private String getTeamId( CloseableHttpClient httpclient, String findTeamName) throws ClientProtocolException, IOException {
        // XXX find_team_by_nameがうまく動かなかったのでallで取ってから名前で突き合わせる
        HttpGet request = new HttpGet( getAllTeamPath());
        request.addHeader( "Content-type", "application/json");

        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute( request);
            System.out.println( response);

            if ( response.getStatusLine().getStatusCode() != 200) {
                throw new IllegalStateException();
            }
            HttpEntity entity = response.getEntity();
            InputStream content = entity.getContent();
            BufferedReader br = new BufferedReader( new InputStreamReader( content));
            String line;
            while ( (line = br.readLine()) != null) {
                System.out.println( line);
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Map<String, String>> result = mapper.readValue( line, Map.class);
                System.out.println( result);
                for ( Map<String, String> teamMap : result.values()) {
                    String teamName = teamMap.get( "display_name");
                    if ( findTeamName.equals( teamName)) {
                        return teamMap.get( "id");
                    }
                }
            }
            EntityUtils.consume( entity);
        }
        finally {
            if ( response != null) {
                response.close();
            }

        }

        throw new IllegalStateException();
    }

    private String getAuthToken( CloseableHttpClient httpclient) throws FileNotFoundException, IOException, UnsupportedEncodingException,
            ClientProtocolException {
        HttpPost request = new HttpPost( getLoginPath());

        String strJson = createLoginJson();

        StringEntity body = new StringEntity( strJson);
        request.addHeader( "Content-type", "application/json");
        request.setEntity( body);

        CloseableHttpResponse response = httpclient.execute( request);
        Header[] tokenHeaders = response.getHeaders( KEY_HEADER_TOKEN);
        // TODO アドレスが不正とか、ユーザ名、パスワードが不正とかちゃんと区別する
        if ( tokenHeaders == null || tokenHeaders.length == 0) {
            throw new IllegalStateException( "認証用トークンが取得できません。チーム名、ユーザ名、パスワードが正しいか確認してください。response=" + response);
        }

        try {
            // XXX これ必要？
            HttpEntity entity = response.getEntity();
            EntityUtils.consume( entity);
        }
        finally {
            response.close();
        }

        String authToken = tokenHeaders[0].getValue();
        return authToken;
    }

    private String createLoginJson() {
        return JsonBuilder.builder().put( KEY_LOGIN_ID, loginId).put( KEY_PASSWORD, password).build();
    }
}
