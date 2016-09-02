package tsyki.mattermost;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Mattermostにチャンネルが追加された場合にその情報を特定チャンネルにポストします
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/01
 */
public class ChannelCreateChecker {

    private static final String KEY_MATTERMOST_URL = "mattermost_url";

    private static final String KEY_POST_CANNEL = "post_channel_name";

    private static final String KEY_HEADER_TOKEN = "Token";

    private static final String API_HOME_PATH = "/api/v3";

    private static final String LOGIN_PATH = "/users/login";

    private static final String LOGIN_JSON_FILE_PATH = "login.json";

    private String mattermostUrl;

    private String postChannelName;

    private String getLoginPath() {
        return mattermostUrl + API_HOME_PATH + LOGIN_PATH;
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
        postChannelName = prop.getProperty( KEY_POST_CANNEL, "timeline");
    }

    public void run() throws IOException, ClientProtocolException, UnsupportedEncodingException {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        String authToken = getAuthToken( httpclient);

        System.out.println( authToken);

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
            // HttpEntity entity = response.getEntity();
            // EntityUtils.consume( entity);
        }
        finally {
            response.close();
        }

        String authToken = tokenHeaders[0].getValue();
        return authToken;
    }

    private String createLoginJson() throws FileNotFoundException, IOException {
        String strJson = "";

        BufferedReader br = null;
        try {
            br = new BufferedReader( new FileReader( new File( LOGIN_JSON_FILE_PATH)));
            String str;
            while ( (str = br.readLine()) != null) {
                strJson += str;
            }
        }
        finally {
            if ( br != null) {
                br.close();
            }
        }
        return strJson;
    }
}
