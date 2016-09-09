package tsyki.mattermost;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.http.client.ClientProtocolException;

import tsyki.mattermost.driver.Channel;
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

	  private static final String KEY_POST_MESSAGE_TEMPALGE = "post_message_template";

    /** 投稿メッセージの中のプロパティの値で置換する文字列のパターン。{{name}} のような形 */
    private static final String PROP_REPLACE_PATTERN_STR = "\\{\\{(.*?)\\}\\}";

    private static final String KEY_INCOMING_WEBHOOK_URL = "incoming_webhook_url";

    private static final String KEY_POST_USER_NAME = "post_user_name";

    private static final String KEY_POST_ICON_URL = "post_icon_url";

    private String mattermostUrl;

    private String teamName;

    private String loginId;

    private String password;

    private String postChannelName;

    private String postMessageTemplate;

    private String incomingWebhookUrl;

    private String postUserName;

    private String postIconUrl;

    private Logger logger = Logger.getLogger( this.getClass().getName());

    public static void main( String[] args) throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String confFilePath = "channel_checker.properties";
        if ( args.length >= 1) {
            confFilePath = args[0];
        }
        String readedChannnelFilePath = "channels";
        if ( args.length >= 2) {
            readedChannnelFilePath = args[1];
        }

        ChannelCreateChecker channelChecker = new ChannelCreateChecker( confFilePath);
        channelChecker.run( readedChannnelFilePath);
    }

    private Properties prop = new Properties();

    public ChannelCreateChecker( String confFilePath) throws IOException {
        loadConfig( confFilePath);
    }

    private void loadConfig( String confFilePath) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream( confFilePath);
            prop.load( new InputStreamReader( is, "UTF-8"));
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
        postChannelName = prop.getProperty( KEY_POST_CANNEL);
        postMessageTemplate = prop.getProperty( KEY_POST_MESSAGE_TEMPALGE);
        incomingWebhookUrl = prop.getProperty( KEY_INCOMING_WEBHOOK_URL);
        postUserName = prop.getProperty( KEY_POST_USER_NAME);
        postIconUrl = prop.getProperty( KEY_POST_ICON_URL);
    }

    public void run( String readedChannelFilePath) throws IOException, ClientProtocolException, UnsupportedEncodingException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        MattermostWebDriver driver = new MattermostWebDriver();
        try {
            driver.setUrl( mattermostUrl);
            driver.login( loginId, password);
            driver.setTeamIdByName( teamName);

            // 非公開チャンネル、DirectMessageの追加は見ない
            List<Channel> channels = driver.getAllPublicChannels();
            // 過去のチャンネル一覧を読込
            File readedChannelFile = new File( readedChannelFilePath);
            // 過去データがなければ書き込んで終わり
            if ( !readedChannelFile.exists()) {
                logger.info( "過去データがないため現在のチャンネルを書き出し終了。");
                writeChannelIds( readedChannelFilePath, channels);
                return;
            }
            // 投稿するチャンネル指定があれば存在するかバリデートしておく
            if ( postChannelName != null && !postChannelName.isEmpty()) {
                String postChannelId = driver.getChannelIdByName( postChannelName);
                if ( postChannelId == null) {
                    throw new IllegalStateException( "ポストするチャンネルを取得できません。name=" + postChannelName);
                }
            }
            // 過去データと比較
            List<String> readedIdx = readChannelIds( readedChannelFilePath);
            for ( Channel channel : channels) {
                // 過去データにない＝新規追加チャンネルである
                if ( !readedIdx.contains( channel.getId())) {
                    logger.info( "新規追加チャンネル発見。name=" + channel.getName() + " display_name=" + channel.getDisplayName() + " id=" + channel.getId());
                    String msg = createPostMessage( channel, postMessageTemplate);
                    driver.postIncomingWebhook( incomingWebhookUrl, msg, postChannelName, postUserName, postIconUrl);
                }
                // debug write
                String format = "id=%s name=%s displayName=%s createAt=%s";
                logger.fine( String.format( format, channel.getId(), channel.getName(), channel.getDisplayName(), channel.getCreateAt()));
            }
            logger.info( "新規チャンネル探索終了");
            writeChannelIds( readedChannelFilePath, channels);
        }
        finally {
            driver.close();
        }
    }

    /**
     * 投稿メッセージのテンプレートをチャンネルの値で置換して返す
     */
    private String createPostMessage( Channel channel, String postMessageTemplate) throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        String resultMsg = postMessageTemplate;
        Pattern p = Pattern.compile( PROP_REPLACE_PATTERN_STR);
        Matcher m = p.matcher( resultMsg);
        while ( m.find()) {
            String propertyName = m.group( 1);
            // 取得したプロパティ名から値を取得
            String value = BeanUtils.getProperty( channel, propertyName);
            // その値で置換
            resultMsg = m.replaceFirst( value);
            m = p.matcher( resultMsg);
        }
        return resultMsg;
    }

    private List<String> readChannelIds( String filePath) throws IOException {
        List<String> ids = Files.readAllLines( Paths.get( filePath));
        return ids;
    }

    private void writeChannelIds( String filePath, List<Channel> channels) throws IOException {
        try (BufferedWriter writer = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( filePath)))) {
            for ( Channel channel : channels) {
                writer.write( channel.getId() + "\n");
            }
        }
    }
}
