package com.focus.xd;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class ProxyTest {
	private static Logger logger = LoggerFactory.getLogger(ProxyTest.class);

	private List<JSONObject> ipProxyList = new ArrayList<JSONObject>();
	
	private List<JSONObject> deadProxyList = new ArrayList<JSONObject>();
	private WebDriver driver = null;

	private ChromeDriverService service = null;

	private JSONObject currentProxy = null;

	private static String chromePath = System.getProperty("user.dir") + "/chromedriver.exe";

	public static void main(String[] args) {
		new ProxyTest().startRun();
	}

	private void startRun() {
		try {
			doInital();
			doSeleniumInit();
			//doOpenPageTest();
			
			doChangeIpsAuto();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
			if (service != null) {
				service.stop();
			}
		}
	}

	private void doInital() throws Exception {
		getProxys();
		if (ipProxyList.size() == 0) {
			throw new Exception("当前无任何代理信息!");
		} else {
			logger.info("当前有可用代理数为【{}】", ipProxyList.size());
		}

	}

	private void getProxys() {
		String ipString = getLocalProxys();
		if (ipString == "") {
			logger.error("当前无任何代理信息！！！！！！");
			return;
		}

		JSONArray newArray = JSONArray.parseArray(ipString);
		Iterator<Object> it = newArray.iterator();
		while (it.hasNext()) {
			JSONArray oneArr = (JSONArray) it.next();
			JSONObject jsOne = new JSONObject();
			jsOne.put("ip", oneArr.getString(0));
			jsOne.put("port", oneArr.getIntValue(1));
			ipProxyList.add(jsOne);
		}
	}

	private String getLocalProxys() {
		String urlString = "http://127.0.0.1:8000/?types=0&count=500";
		String result = "";
		try {
			// 根据地址获取请求
			HttpGet request = new HttpGet(urlString);
			// 获取当前客户端对象
			HttpClient httpClient = HttpClients.createDefault();
			// 通过请求对象获取响应对象
			HttpResponse response = httpClient.execute(request);

			// 判断网络连接状态码是否正常(0--200都数正常)
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				result = EntityUtils.toString(response.getEntity(), "utf-8");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	private void doSeleniumInit() throws Exception {
		service = new ChromeDriverService.Builder().usingDriverExecutable(new File(chromePath)).usingAnyFreePort()
				.build();
		service.start();

		currentProxy = ipProxyList.get(0);
		driverSetting(currentProxy);
	}

	private void driverSetting(JSONObject jsonObject) {
		String proxyIpAndPort = jsonObject.getString("ip") + ":" + jsonObject.getIntValue("port");
		logger.info("当前选用的代理为【{}】", proxyIpAndPort);

		DesiredCapabilities cap = DesiredCapabilities.chrome();
		Proxy proxy = new Proxy();
		proxy.setProxyType(ProxyType.MANUAL);		
		proxy.setHttpProxy(proxyIpAndPort).setSocksProxy(proxyIpAndPort).setFtpProxy(proxyIpAndPort)
				.setSslProxy(proxyIpAndPort);
		cap.setCapability(CapabilityType.ForSeleniumServer.AVOIDING_PROXY, true);
		cap.setCapability(CapabilityType.ForSeleniumServer.ONLY_PROXYING_SELENIUM_TRAFFIC, true);
		System.setProperty("http.nonProxyHosts", "localhost");
		cap.setCapability(CapabilityType.PROXY, proxy);
		
		if (driver != null) {
			driver.quit();
			driver = null;
		}
		driver = new ChromeDriver(cap);
		driver.manage().timeouts().implicitlyWait(15, TimeUnit.SECONDS);
	}

	private void doChangeIpsAuto()
	{
		while(true)
		{
			logger.info("开始打开测试页面！！！！！");
			doOpenPageTest();		
			deadProxyList.add(currentProxy);
			
			ipProxyList.removeAll(deadProxyList);
			if (ipProxyList.size()==0 || ipProxyList.size() <= 50) {
				logger.info("退出代理自动切换！！！！！");
				break;
			}
			logger.info("开始切换代理！！！！！");
			currentProxy = ipProxyList.get(0);
			driverSetting(currentProxy);
			delay(1000);
		}
	}
	
	
	
	
	private void doOpenPageTest() {
		String url = "http://ip.chinaz.com/getip.aspx";
		driver.get(url);
		delay(5000);
	}

	private void delay(int millis) {
		try {
			Thread.sleep(millis);
		} catch (Exception e) {
		}
	}
}
