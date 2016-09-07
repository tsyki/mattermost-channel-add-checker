package tsyki.mattermost;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.client.ClientProtocolException;

import tsyki.mattermost.driver.MattermostWebDriver;

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

    private String mattermostUrl;

    private String teamName;

    private String loginId;

    private String password;

    private String postChannelName;

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
        teamName = prop.getProperty( KEY_TEAM_NAME);
        loginId = prop.getProperty( KEY_LOGIN_ID);
        password = prop.getProperty( KEY_PASSWORD);
        postChannelName = prop.getProperty( KEY_POST_CANNEL, "timeline");
    }

    public void run() throws IOException, ClientProtocolException, UnsupportedEncodingException {
        MattermostWebDriver driver = new MattermostWebDriver();
        driver.setUrl( mattermostUrl);
        driver.login( loginId, password);
        driver.setTeamIdByName( teamName);
        // TODO Mapではなくクラスにしたい
        List<Map<String, String>> channels = driver.getAllChannel();
        for ( Map<String, String> map : channels) {
            String channelId = map.get( "id");
            String channelName = map.get( "display_name");
            System.out.println( "id=" + channelId + " name=" + channelName);
        }
    }

}
