package com.focus.xd;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * 代理操作类 获取代理信息，删除已死代理
 *
 */
public class IPProxyAccess {

	private static String getMainUrl = "http://127.0.0.1:8000/?types=0&count=500&country=国内";

	private static String delMainUrl = "http://127.0.0.1:8000/delete?ip=";

	/**
	 * 获取代理Json字符串
	 * 
	 * @return
	 */
	public static String getIPProxyJsonStr() {
		return doGetHttp(getMainUrl);
	}

	/**
	 * 删除已死的代理列表
	 * 
	 * @param deadList
	 */
	public static void deleteDeadProxys(List<String> deadList) {
		for (String ipString : deadList) {
			String ip = ipString.substring(0, ipString.indexOf(":"));
			String ret = doGetHttp(delMainUrl + ip);
			System.out.println("删除已死的代理地址【" + ip + "】返回值为： " + ret);
			try {
				Thread.sleep(100);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 进行http访问
	 * 
	 * @param destUrl
	 * @return
	 */
	private static String doGetHttp(String destUrl) {
		String result = "";
		HttpClient httpClient = null;
		try {
			HttpGet request = new HttpGet(destUrl);

			httpClient = HttpClients.createDefault();
			HttpResponse response = httpClient.execute(request);

			if (response.getStatusLine().getStatusCode() == 200)
				result = EntityUtils.toString(response.getEntity(), "utf-8");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (httpClient != null) {
				httpClient = null;
			}
		}
		return result;
	}

	public static void main(String[] args) {
		 doGetIpsTest();
		 doIpsDelTest();
	}

	private static void doGetIpsTest() {
		String jsString = "[['125.88.74.122', 83, 22], ['61.191.41.130', 80, 22], ['58.217.195.141', 80, 22],"
				+ "['111.13.109.27', 80, 22], ['122.142.77.84', 8080, 22],"
				+ "['14.199.124.204', 80, 7], ['124.88.67.34', 843, 7], ['120.27.113.72', 8888, 7]]";

		JSONArray jsonArray = JSONObject.parseArray(jsString);
		Iterator<Object> it = jsonArray.iterator();

		List<String> ipProxyList = new ArrayList<>();
		while (it.hasNext()) {
			JSONArray obArray = (JSONArray) it.next();
			String ipString = obArray.getString(0) + ":" + obArray.getIntValue(1);
			if (ipProxyList.contains(ipString) == false) {
				ipProxyList.add(ipString);

			}
		}
		// 排序
		Collections.sort(ipProxyList);
		int modePara = 3;

		System.out.println(ipProxyList);

		int count = ipProxyList.size();

		List<String> reList = new ArrayList<>();
		if (modePara != 0) {
			for (int i = 0; i < count; i++) {
				int a = i % modePara;
				System.out.println(a);
				if (a != 0) {
					reList.add(ipProxyList.get(i));
				}
			}
		}
		ipProxyList.removeAll(reList);

		System.out.println(ipProxyList);
	}

	private static void doIpsDelTest() {
		List<String> deadList = new ArrayList<>();
		deadList.add("114.215.153.151:8080");
		deadList.add("115.231.175.68:8081");
		deadList.add("117.90.5.224:9000");
		deadList.add("118.178.135.168:3128");

		deleteDeadProxys(deadList);
	}
}