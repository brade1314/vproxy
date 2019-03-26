package net.cassite.vproxy.test;

import net.cassite.vproxyx.WebSocksProxyAgent;
import org.junit.Test;

/**
 * <p>Title: ProxyAgentStart </p>
 * <p>Description:  </p>
 * <p>Company: aiWei </p>
 *
 * @author zzhengmin
 * @date 2019/3/11 14:09
 */
public class ProxyAgentStart {
    public static void main(String[] args) throws Exception {
        String url = ProxyAgentStart.class.getClassLoader().getResource("websocks-agent-example.conf").getPath();
        WebSocksProxyAgent.main0(new String[]{url});
    }
}
