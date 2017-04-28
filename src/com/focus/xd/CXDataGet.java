package com.focus.xd;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CXDataGet {
	private static Logger LOG = LoggerFactory.getLogger(CXDataGet.class);
	private static ChromeDriverService service = null;
	private static WebDriver driver = null;

	/**
	 * 数据抓取存储路径
	 */
	private static String savePath = System.getProperty("user.dir").replace('\\', '/') + "/XD";
	
	/**
	 * 驱动程序路径
	 */
	private static String driverExePath = System.getProperty("user.dir").replace('\\', '/') + "/chromedriver.exe";
	
	/**
	 * 需抓取的公司列表文件全路径
	 */
	private static String companyInfo = System.getProperty("user.dir").replace('\\', '/') + "/MY.txt";
	
	/**
	 * 已抓取的公司列表文件全路径
	 */
	private static String doneInfo = System.getProperty("user.dir").replace('\\', '/') + "/done.txt";

	/**
	 * 待抓取的公司列表
	 */
	private List<String> companyList = new ArrayList<String>();

	/**
	 * 已抓取的公司列表
	 */
	private List<String> doneCompList = new ArrayList<String>();

	/**
	 * 代理ip列表
	 */
	private List<String> ipProxyList = new ArrayList<>();
	
	/**
	 * 已被标识为不可用的ip列表
	 */
	private List<String> deadIplist = new ArrayList<>();
	
	private String mainurl = "";

	/**
	 * 已抓取数量，用于判断是否该睡一会再抓
	 */
	private int  MAX_COUNT = 0;
	
	private static int  PAGE_MAX_LOADTIME = 15;

	/**
	 * 锁对象
	 */
	private static Object lockO = new Object();
	
	/**
	 * 定时代理操作服务，用于定时加载新代理，删除已死代理
	 */
	private ScheduledExecutorService proxyAccess;
	
	/**
	 * 当前的代理地址ip+ port
	 */
	private String currentProxy = null;
	
	/**
	 * 代理编号基数，只使用此基数倍数的代理
	 * 防止同一时间多个程序同时使用1个代理，这样此代理一会就被屏蔽
	 */
	private int modePara = 0;
	
	/**
	 * 抓取过程中出现加载不安全脚本时，标识此公司名称，防止漏掉
	 */
	private String specialStr = "";

	/**
	 * 程序入口方法
	 * @param args
	 */
	public static void main(String[] args) {
		new CXDataGet().runMain(args);
	}

	/**
	 * 程序启动方法
	 * @param args
	 */
	private void runMain(String[] args) {
		try {
			if (args.length > 0) {
				LOG.info("IP筛选采取取余【{}】为基准", args[0]);
				modePara = Integer.valueOf(args[0]);
			}

			this.proxyAccess = Executors
					.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("proxyAccess").build());
			this.proxyAccess.scheduleWithFixedDelay(new proxyAccessRunnable(), 5L, 20L, TimeUnit.MINUTES);

			getAllProxyIps();

			doInital();

			doOpenSearchPage();

			for (String com : this.companyList) {
				specialStr = "";
				if (this.ipProxyList.size() == 0) {
					LOG.info("当前无可用代理，睡3分钟再加载一下！");
					delay(3 * 60 * 1000);
					doDeleteAndLoadProxy();
				}

				if (this.MAX_COUNT >= 15) {
					LOG.info("已抓取超过15个，睡一会儿再干！！！！...");
					delay(5 * 60 * 1000);
					this.MAX_COUNT = 0;
				}

				LOG.info("当前信用查询公司名称为【{}】", com);
				searchKey(com);
				getCompanyCXData(com);
				SaveTxtUtil.addContentToFile(doneInfo, specialStr + com);
				delay(20000);
				this.MAX_COUNT += 1;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
			if (service != null) {
				service.stop();
			}

			if (this.proxyAccess != null) {
				this.proxyAccess.shutdown();
				try {
					if (!this.proxyAccess.awaitTermination(1L, TimeUnit.SECONDS))
						this.proxyAccess.shutdownNow();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 进行初始化操作，加载代理列表，获取待抓取公司名称信息
	 * @throws Exception
	 */
	private void doInital() throws Exception {
		LOG.info("初始化服务...");
		if (this.ipProxyList.size() == 0) {
			throw new Exception("无可用代理，退出！！！！！！！");
		}
		LOG.info("当前有可用代理【{}】", Integer.valueOf(this.ipProxyList.size()));
		currentProxy = ipProxyList.get(0);
		setProxySetting(currentProxy);

		this.companyList = ReadLocalTxt.getLocalTxt(companyInfo);
		this.doneCompList = ReadLocalTxt.getLocalTxt(doneInfo);
		this.companyList.removeAll(this.doneCompList);
		Files.createDirectories(Paths.get(savePath, new String[0]), new FileAttribute[0]);
	}

	/**
	 * 打开搜索主页
	 * @throws Exception
	 */
	private void doOpenSearchPage() throws Exception {
		boolean ret = false;
		try {

			LOG.info("开始第一次访问...");
			this.mainurl = "https://xinyong.1688.com/credit/publicCreditSearch.htm";
			driver.get(this.mainurl);
			delay(5000);

			if (judgeIfIpForbide()) {
				LOG.warn("当前IP【{}】已被屏蔽，立刻马上更换！！！！！！！！...", currentProxy);
				ret = doChangeProxyRightNow();
			}
		} catch (Exception e) {
			LOG.error("访问出错或者超时！！！！需要立刻马上更换当前IP【{}】！！！！！！", currentProxy);
			ret = doChangeProxyRightNow();
		}

		if (ret == false) {
			throw new Exception("第一次登录访问就失败了，不用再干了，速度请求支援！！！");
		}
	}

	/**
	 * 进行代理更换操作，循环代理列表，设置代理，并进行代理验证
	 * @return true，有可用代理，false，没有能用的代理
	 */
	private boolean doChangeProxyRightNow() {
		this.ipProxyList.removeAll(this.deadIplist);
		synchronized (lockO) {
			for (String nexJs : ipProxyList) {
				this.currentProxy = nexJs;
				setProxySetting(nexJs);
				if (doTestCurrentProxy()) {
					LOG.info("当前代理【{}】测试成功，准备采用！！！！", currentProxy);
					return true;
				}
				delay(100);
			}
		}
		LOG.info("当前更换代理失败，已无可用代理，哦多改！！！！");
		this.ipProxyList.removeAll(this.deadIplist);
		doDeleteAndLoadProxy();
		return false;
	}

	/**
	 * 测试当前代理是否可用
	 * @return true，可用；false，不可用。
	 */
	private boolean doTestCurrentProxy() {
		try {
			driver.get(this.mainurl);
			delay(2000);
			if (judgeIfIpForbide()) {
				return false;
			}
			return true;
		} catch (Exception e) {
			doAddDeaProxy(currentProxy);	
		}
		return false;
	}

	/**
	 * 设置代理信息，先关闭上一个代理服务以及浏览器
	 * @param proxyIpAndPort
	 */
	private void setProxySetting(String proxyIpAndPort) {
		// String proxyIpAndPort = js.getString("ip") + ":" +
		// js.getIntValue("port");
		// proxyIpAndPort = "111.72.126.190:808";

		LOG.info("当前选用的代理为【{}】", proxyIpAndPort);

		DesiredCapabilities cap = DesiredCapabilities.chrome();
		Proxy proxy = new Proxy();
		proxy.setProxyType(ProxyType.MANUAL);
		proxy.setHttpProxy(proxyIpAndPort).setSocksProxy(proxyIpAndPort).setFtpProxy(proxyIpAndPort)
				.setSslProxy(proxyIpAndPort);
		cap.setJavascriptEnabled(true);
		System.setProperty("http.nonProxyHosts", "localhost");
		cap.setCapability(CapabilityType.PROXY, proxy);

		/**
		 *  此参数貌似没用
		 */
		ChromeOptions options = new ChromeOptions();
		options.addArguments("disable-web-security");
		cap.setCapability(ChromeOptions.CAPABILITY, options);

		if (driver != null) {
			driver.quit();
			driver = null;
		}
		if (service != null) {
			service.stop();
			service = null;
		}

		service = new ChromeDriverService.Builder().usingDriverExecutable(new File(driverExePath)).usingAnyFreePort()
				.build();
		try {
			service.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		driver = new ChromeDriver(cap);
		
		driver.manage().timeouts().pageLoadTimeout(PAGE_MAX_LOADTIME, TimeUnit.SECONDS);
		
		driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS);
		delay(1000);
	}

	/**
	 * 获取代理列表信息
	 * 先解析json字符串，再排序，捞取基数对应的列表
	 */
	private void getAllProxyIps() {
		String proxyStr = IPProxyAccess.getIPProxyJsonStr();
		if (proxyStr == "") {
			LOG.error("获取代理ip失败！！！");
			return;
		}

		JSONArray jsonArray = JSONObject.parseArray(proxyStr);
		Iterator<Object> it = jsonArray.iterator();

		while (it.hasNext()) {
			JSONArray obArray = (JSONArray) it.next();
			String ipString = obArray.getString(0) + ":" + obArray.getIntValue(1);
			if (ipProxyList.contains(ipString) == false) {
				synchronized (lockO) {
					this.ipProxyList.add(ipString);
				}
			}
		}
		// 排序
		Collections.sort(ipProxyList);

		List<String> modeList = new ArrayList<>();

		if (modePara != 0) {
			for (int i = 0; i < ipProxyList.size(); i++) {
				if (i % modePara != 0) {
					modeList.add(ipProxyList.get(i));
				}
			}
		}

		ipProxyList.removeAll(modeList);
	}

	/**
	 * 进行公司名称搜索。
	 * 1. 首先用进行地址访问，若没有发生异常，则判断当前的信息是否是已被屏蔽，若没有被屏蔽，则进行信息获取；
	 * 若已被屏蔽，则进行代理更换，更换时若更换失败了则进行2操作，否则再次访问该地址。
	 * 2. 有异常，则进行多次的代理更换操作，直至可用；若最后在等待了10次操作还是失败时，会抛出异常，程序中断退出。
	 * @param key
	 * @throws Exception
	 */
	private void searchKey(String key) throws Exception {
		String url = this.mainurl + "?key=" + key;
		try {
			driver.get(url);
			delay(2000);

			if (judgeIfIpForbide()) {
				boolean ret = doChangeProxyRightNow();
				if (ret == false) {
					String exString = "再查询当前公司【" + key + "】不断跟换代理失败，还是失败了，扔出异常！";
					throw new Exception(exString);
				}
				delay(1000);
				driver.get(url);
			}
		} catch (Exception e) {
			LOG.info("当前代理【{}】使用时发生了错误，得换代理！！！", currentProxy);
			doAddDeaProxy(currentProxy);		
			doAgainAndAgain(url);
		}
	}

	/**
	 * 进行10次代理的更换操作。
	 * 1. 更换代理，若没有可用代理则睡3分钟，因为此时有另一个定时器会定时加载新的代理信息。
	 * 2. 若有可用代理，则开始访问地址，访问出错，则继续换与等待了；访问成功，则跳出循环。
	 * 3. 若10次还是失败了，则抛出异常，等待人工救援
	 * @param url
	 * @throws Exception
	 */
	private void doAgainAndAgain(String url) throws Exception {
		LOG.info("准备尝试10次更换代理,直至有可用代理！！！");
		boolean ret = false;
		for (int i = 1; i < 11; i++) {
			LOG.info("當前第【{}】次嘗試..........", i);
			try {
				ret = doChangeProxyRightNow();
				if (ret == false) {
					LOG.info("先睡3分钟再说....");
					delay(3 * 60 * 1000);
					continue;
				}
				delay(1000);
				driver.get(url);
				LOG.info("当前代理【{}】终于可用！泪流满面，准备干活！！！！！！！", currentProxy);
				ret = true;
				break;
			} catch (Exception e) {
				LOG.info("当前代理【{}】使用时又发生了错误，继续下一次尝试！！！", currentProxy);
				doAddDeaProxy(currentProxy);
			}
		}

		if (ret == false) {
			throw new Exception("10次尝试都失败了,只能抛出异常,等待救援！！！！！");
		}
	}

	/**
	 * 判断代理是否被阿里屏蔽，通过跳转的地址栏与返回显示的信息
	 * @return true，已被屏蔽；false，未被屏蔽
	 */
	private boolean judgeIfIpForbide() {
		String currUrl = driver.getCurrentUrl();
		if (currUrl.startsWith("http://alisec.1688.com/")) {
			doAddDeaProxy(currentProxy);	
			return true;
		}
		if (judgeIfCantVisit()) {
			doAddDeaProxy(currentProxy);	
			return true;
		}

		return false;
	}

	/**
	 * 查看当前页是否有无法访问的提示
	 * 
	 * @return true，不能访问；false，可以访问
	 */
	private boolean judgeIfCantVisit() {
		try {
			driver.findElement(By.xpath("//div[@id='main-frame-error']"));
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * 获取公司的诚信信息。
	 * 首先获取第一页数据，若无信息，则跳出；尝试点击下一页，若点击下一页失败，则返回
	 * @param 公司名称
	 */
	private void getCompanyCXData(String pString) {
		//页面编号
		int times = 1;
		//第一页标识
		boolean firstFlag = true;
		while (true) {
			try {
				LOG.info("当前页面编号: 【" + times + "】");
				boolean ret = getOnePageContent(pString, firstFlag);
				if (!ret) {
					break;
				}
				
				LOG.info("尝试跳转至下一个页面...");
				boolean jump = waitForElementToClick(By.xpath(
						"//button[@class='next-btn next-btn-normal next-btn-medium next-pagination-item next']"));
				
				if (!jump) {
					break;
				}
				delay(2000);
				times++;
				firstFlag = false;
				delay(10);
			} catch (Exception e) {
				e.printStackTrace();
				LOG.error("执行页面内容获取发生未知异常" + e);
				break;
			}
			delay(100);
		}
	}

	/**
	 * 获取当前页面信息。
	 * 1. 先判断是否搜索无结果，若是则返回失败；
	 * 2. 判断是否出现阿里针对频繁访问进行的操作行为，若是则更换代理，并返回失败；
	 * 3. 获取有诚信信息的公司元素列表，循环元素列表进行信息保存
	 * @param 存储信息的父路径
	 * @param 是否为第一页标识
	 * @return
	 * @throws Exception
	 */
	private boolean getOnePageContent(String parentPath, boolean judgeflag) throws Exception {
		List<WebElement> noList = waitForElementsListToLoad(
				By.xpath("//div[@class='null-data']//span[contains(text(),'搜索无结果')]"));
		if (noList.size() != 0) {
			LOG.info("当前页面搜索无结果,返回！");
			return false;
		}

		//出现页面加载现场，更换代理，并特殊标识此公司
		List<WebElement> forbList = waitForElementsListToLoad(
				By.xpath("//div[@class='null-data']//span[contains(text(),'Loading')]"));
		if (forbList.size() != 0) {
			LOG.error("当前页面出现加载情况，立刻马上需要切换代理，此公司名称添加非正常标识!");
			specialStr = "非正常！！！";
			doChangeProxyRightNow();
			return false;
		}

		
		List<WebElement> copanyList = waitForElementsListToLoad(By.xpath("//section[@class='cr-company-card']"));
		if (copanyList.size() == 0) {
			LOG.info("当前页面无任何有诚信等级的公司，返回！");
			// 只有一页的，没有任何内容则返回失败，有多页的需返回正常才能向下一页进发
			if (judgeflag) {
				return false;
			} else {
				return true;
			}
		}

		for (WebElement element : copanyList) {
			getCompanyInfo(parentPath, element);
			delay(100);
		}

		return true;
	}

	/**
	 * 获取公司信息以及诚信信息，并保存至指定文件夹
	 * @param parentPath
	 * @param companyEle
	 */
	private void getCompanyInfo(String parentPath, WebElement companyEle) {
		WebElement idElement = companyEle
				.findElement(By.xpath(".//div[@class='cr-company-card-main']//span[@class='title-content']"));
		String idString = idElement.getText();
		if (idString.isEmpty()) {
			delay(1000);
			idString = idElement.getText();
			if (idString.isEmpty()) {
				idString = Long.valueOf(new Date().getTime()).toString();
				LOG.info("无法获取公司名称，使用当前时间milliseconds " + idString);
			}
		}

		String curDic = savePath + "/" + parentPath + "/" + idString;
		SaveTxtUtil.createLocalFolder(curDic);

		String comTxt = companyEle.getText();

		WebElement subElement = companyEle
				.findElement(By.xpath(".//div[@class='cr-company-card-sub']//p[@class='cr-company-card-level']"));
		String levelStr = subElement.getText();
		comTxt = comTxt + "\r\n诚信等级为：\r\n" + levelStr;
		LOG.info("公司名称 【" + idString + " 】，诚信等级为：" + levelStr);

		SaveTxtUtil.saveTextToFile(curDic + "/cxInfo.txt", comTxt);
	}

	/**
	 * 等待元素加载
	 * @param item
	 * @return
	 */
	private List<WebElement> waitForElementsListToLoad(By item) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, 5L);
			return (List<WebElement>) wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(item));
		} catch (Exception e) {
		}
		return new ArrayList<WebElement>();
	}

	/**
	 * 等待元素点击
	 * @param item
	 * @return true，可点击，false，无法点击
	 */
	private boolean waitForElementToClick(By item) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, 5L);
			WebElement e1 = (WebElement) wait.until(ExpectedConditions.elementToBeClickable(item));
			delay(300);
			e1.click();
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	@SuppressWarnings("unused")
	/**
	 * 此处为登录操作，废弃
	 * @throws Exception
	 */
	private void doOpenMainPage() throws Exception {
		LOG.info("开始访问并登录...");
		driver.get("https://cheng.xin/login.htm");
		delay(2000);

		WebElement elementlogin = driver.findElement(By.id("J-show-login"));
		Actions action = new Actions(driver);
		action.moveToElement(elementlogin);
		action.click().build().perform();
		action.release();
		delay(3000);

		driver.switchTo().frame("alibaba-login-box");

		WebElement elementuser = driver.findElement(By.xpath("//input[@id='fm-login-id']"));
		elementuser.sendKeys(new CharSequence[] { "maomaocheer" });
		WebElement elemenpwd = driver.findElement(By.xpath("//input[@id='fm-login-password']"));
		elemenpwd.sendKeys(new CharSequence[] { "123cannymao" });
		elemenpwd.submit();
		delay(3000);

		driver.switchTo().defaultContent();

		WebElement search = driver.findElement(By.xpath("//a[@title='支持全国企业信息查询']"));
		Actions action1 = new Actions(driver);
		action1.moveToElement(search);
		action1.click().build().perform();
		action1.release();
		delay(3000);

		Set<String> winHandels = driver.getWindowHandles();
		List<String> it = new ArrayList<String>(winHandels);
		driver.switchTo().window((String) it.get(1));
		Thread.sleep(1000L);
		String url = driver.getCurrentUrl();
		System.out.println(url);
	}

	/**
	 * 代理定时器操作
	 *
	 */
	private class proxyAccessRunnable implements Runnable {
		@Override
		public void run() {
			try {
				LOG.info("开始定时获取可用代理ip，删除已死代理！！！！");
				doDeleteAndLoadProxy();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 先把已死代理进行删除，再加载新的
	 */
	private void doDeleteAndLoadProxy() {
		List<String> deleteList = new ArrayList<>();
		deleteList.addAll(deadIplist);
		IPProxyAccess.deleteDeadProxys(deleteList);
		synchronized (lockO) {
			deadIplist.removeAll(deleteList);
		}

		getAllProxyIps();
	}
	
	/**
	 * 添加不可用的代理
	 * @param ipports
	 */
	private void doAddDeaProxy(String ipports)
	{
		if (deadIplist.contains(ipports)==false) {
			deadIplist.add(ipports);
		}
	}
	
	/**
	 * 睡一会儿
	 * @param seconds
	 */
	private void delay(int seconds) {
		try {
			Thread.sleep(seconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}