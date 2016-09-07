package tsyki.mattermost.driver;

import java.util.Date;

/**
 * Mattermostのチャンネル情報
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/07
 */
public class Channel {
    private String id;

    private String name;

    private String displayName;

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

}
