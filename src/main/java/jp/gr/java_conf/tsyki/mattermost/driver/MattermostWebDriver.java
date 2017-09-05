/*
 * Copyright (c) 2017 Toshiyuki Imaizumi
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php 
 */
package jp.gr.java_conf.tsyki.mattermost.driver;

import java.io.IOException;
import java.util.List;

import org.apache.http.client.ClientProtocolException;

/**
 * MattermostのAPIを叩くためのインタフェース<BR>
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2017/09/05
 */
public interface MattermostWebDriver {

    /**
     * mattermostのホスト名を設定する。<BR>
     * 例：http://yourdomain.com。最後に/は付けない
     */
    void setUrl( String mattermostUrl);

    /**
     * ログインを行い認証用トークンを保持します
     * @param loginId
     * @param password
     * @throws ClientProtocolException
     * @throws IOException
     */
    void login( String loginId, String password) throws ClientProtocolException, IOException;

    /**
     * 利用するチームを設定する。<BR>
     * チームに依存したAPIを利用する場合は事前にこのメソッドを呼ぶ必要がある
     * @param teanName チームの名称(表示名ではなくURLに現れるもの)
     * @throws IOException
     * @throws ClientProtocolException
     */
    void setTeamIdByName( String teamName) throws ClientProtocolException, IOException;

    /**
     * ログインユーザが参照可能な全ての公開チャンネルを返します(参加していないチャンネルも含む)
     * @return
     * @throws IOException
     * @throws ClientProtocolException
     */
    List<Channel> getAllPublicChannels() throws ClientProtocolException, IOException;

    /**
     * 指定のnameを持つチャンネルのIDを返す
     * @param findChannelName
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    String getChannelIdByName( String postChannelName) throws ClientProtocolException, IOException;

    /**
     * Incoming Webhookを使って投稿します
     * @param webhookUrl Incoming Webhookで表示されるURL
     * @param msg 投稿する文面
     * @param postChannelName 投稿するチャンネル。空ならばIncoming Webhookで指定したチャンネルが使われる
     * @param postUserName 投稿するユーザ名。空ならばデフォルト(Webhook)となる
     * @param postIconUrl 投稿するユーザのアイコン。空ならばWebhookのデフォルトのものとなる
     */
    void postIncomingWebhook( String incomingWebhookUrl, String msg, String postChannelName, String postUserName, String postIconUrl)
            throws ClientProtocolException, IOException;

    /**
     * コネクションを閉じ、利用を完了する。
     * @throws IOException
     */
    void close() throws IOException;

}
