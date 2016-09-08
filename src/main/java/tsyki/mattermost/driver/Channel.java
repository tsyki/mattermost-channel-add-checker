package tsyki.mattermost.driver;

import java.util.Date;

/**
 * Mattermostのチャンネル情報
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/07
 */
public class Channel {
    private static final String TYPE_PUBLIC = "O";

    private static final String TYPE_PRIVATE = "P";

    private static final String TYPE_DIRECT_MESSAGE = "D";

    private String id;

    private String name;

    private String displayName;

    private String type;

    private Date createAt;

    private Date updateAt;

    public String getId() {
        return id;
    }

    public void setId( String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName( String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType( String type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName( String displayName) {
        this.displayName = displayName;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt( Date createAt) {
        this.createAt = createAt;
    }

    public Date getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt( Date updateAt) {
        this.updateAt = updateAt;
    }

    public boolean isPublicChannel() {
        return TYPE_PUBLIC.equals( type);
    }

    public boolean isPrivateChannel() {
        return TYPE_PRIVATE.equals( type);
    }

    public boolean isDireceMessage() {
        return TYPE_DIRECT_MESSAGE.equals( type);
    }

    @Override
    public String toString() {
        return "Channel [id=" + id + ", name=" + name + ", displayName=" + displayName + ", type=" + type + ", createAt=" + createAt + ", updateAt="
                + updateAt + "]";
    }

}
