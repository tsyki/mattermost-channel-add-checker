# mattermost-add-channel-checker
mattermostにチャンネルが追加された際に、その情報を指定チャンネルにポストするプログラム

使い方
------

1. MattermostからIncoming Webhookを登録し、ポスト用のURLを発行する
2. jarを作成  
cloneした後、mvn packageを実行。生成された以下のjarを使う  
mattermost-channel-add-checker-jar-with-dependencies.jar
3. 作成したjarを適当な箇所に配置  
  /home/hogehoge/channel-add-checker.jarに置いたとする
4. channel_checker.propertiesをjarと同じ場所に置き、コメントを参考に項目を埋める
5. cronで定期的に呼ばれるようにする  
  2分ごとに実行する例  
    */2 * * * * cd /home/hogehoge/;java -jar channel-add-checker.jar
  
これでチャンネルが増えた際に以下のメッセージが自動で投稿される  
    「新規チャンネルが追加されました。name=hogehoge 名称=ほげほげ」  
  
投稿する文面を変えたい場合はchannel_checker.propertiesのpost_message_templateの値を変更する。

## License
[MIT](https://github.com/tsyki/mattermost-channel-add-checker/blob/master/LICENSE)
