package tsyki.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * key,valueを指定してJson文字列を作成する。<BR>
 * 利用例<BR>
 * 
 * <pre>
 * JsonBuilder.builder().put( &quot;user_name&quot;, &quot;hoge&quot;).put( &quot;password&quot;, &quot;piyo&quot;).build();
 * </pre>
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/02
 */
public class JsonBuilder {
    private Map<String, String> valueMap = new LinkedHashMap<String, String>();

    public static JsonBuilder builder() {
        return new JsonBuilder();
    }

    public JsonBuilder put( String key, String value) {
        valueMap.put( key, value);
        return this;
    }

    public String build() {
        StringBuilder jsonStringBuilder = new StringBuilder();
        jsonStringBuilder.append( "{");

        for ( Iterator<Entry<String, String>> iterator = valueMap.entrySet().iterator(); iterator.hasNext();) {
            Entry<String, String> entry = iterator.next();
            jsonStringBuilder.append( "\"");
            jsonStringBuilder.append( entry.getKey());
            jsonStringBuilder.append( "\"");
            jsonStringBuilder.append( ":");
            jsonStringBuilder.append( "\"");
            jsonStringBuilder.append( entry.getValue());
            jsonStringBuilder.append( "\"");
            if ( iterator.hasNext()) {
                jsonStringBuilder.append( ",");
            }
        }
        jsonStringBuilder.append( "}");
        return jsonStringBuilder.toString();

    }
}
