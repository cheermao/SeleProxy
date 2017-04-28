package com.focus.xd;

import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 诚信数据解析
 * @author shifeiyue
 *
 */
public class CXDataParse {
	private static Logger LOG = LoggerFactory.getLogger(CXDataParse.class);
	
	/**
	 * 数据文件根目录
	 */
	private static String rootDir = System.getProperty("user.dir").replace('\\', '/') + "/XDALL";

	/**
	 * 数据库连接对象
	 */
	private Connection conn = null;
	
	/**
	 * 数据库操作对象
	 */
	private PreparedStatement stment = null;

	/**
	 * 默认数据库的批量提交数值
	 */
	private static final int DEFAULT_BATCH_SIZE = 100;

	/**
	 * 待入库的诚信数据列表
	 */
	private List<CXObject> cxList = new ArrayList<>();

	/**
	 * 记录编号，递增，因为是批量入库，因此没使用sequence
	 */
	private int recordId = 0;

	/**
	 * 程序入口
	 * @param args
	 */
	public static void main(String[] args) {
		new CXDataParse().runMain(args);

	}

	/**
	 * 程序启动方法
	 * @param args
	 */
	private void runMain(String[] args) {
		try {
			//加载数据库记录编号，以便递增
			if (args.length>0) {
				recordId = Integer.valueOf(args[0]);
			}
			doInital();
			parseFileAndInsertRecords();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (stment != null) {
					stment.close();
				}
				if (conn != null) {
					conn.close();
				}
			} catch (Exception e2) {
			}
		}
	}

	/**
	 * 进行初始化操作，初始化数据连接
	 */
	private void doInital() {
		try {
			Class.forName("oracle.jdbc.driver.OracleDriver");// 加载Oracle驱动程序
			System.out.println("开始尝试连接数据库....");
			String url = "jdbc:oracle:" + "thin:@192.168.50.84:1521:orcl";
			String user = "BI_READ";
			String password = "BI_READ";
			conn = DriverManager.getConnection(url, user, password);// 获取连接
			conn.setAutoCommit(false);
			stment = conn.prepareStatement("INSERT INTO DM_SMALL_LOANS_CXDATA VALUES (?,?,?,?,?,?,?,?,?)");
			System.out.println("已连接数据库！");
		} catch (Exception e) {
			LOG.error("无法连接数据库");
			e.printStackTrace();
		}
	}

	/**
	 * 获取待解析的文件，并解析入库
	 */
	private void parseFileAndInsertRecords() {
		File rFile = new File(rootDir);
		List<File> fileList = getCXDataFiles(rFile);
		for (File element : fileList) {
			parseCXInfo(element);
			//大于批量入库值，就入库
			if (cxList.size() >= DEFAULT_BATCH_SIZE) {
				doBitchInsert();
				cxList.clear();
				delay(1000);
			}
		}
		//最后剩余记录的入库
		if (cxList.size() > 0) {
			doBitchInsert();
			cxList.clear();
		}
	}

	/**
	 * 递归获取所有的诚信数据文件
	 * @param rootDir
	 * @return
	 */
	private List<File> getCXDataFiles(File rootDir) {
		List<File> candidateFiles = new ArrayList<File>();
		if (rootDir == null || !rootDir.isDirectory()) {
			return candidateFiles;
		}
		for (File file : rootDir.listFiles()) {
			if (file.isDirectory()) {
				candidateFiles.addAll(getCXDataFiles(file));
			} else {
				if (file.getName().equals("cxInfo.txt")) {
					candidateFiles.add(file);
				} else {
					LOG.info("发现未知的文件，【{}】", file.getPath());
				}
			}
		}
		return candidateFiles;
	}

	/**
	 * 解析诚信数据文件
	 * @param cxFile
	 */
	private void parseCXInfo(File cxFile) {
		List<String> dataInfo = ReadLocalTxt.getCXTxt(cxFile.getPath());
		if (dataInfo.size()!=0) {
			getCXObject(dataInfo);
		}
	}

	/**
	 * 获取诚信数据并生成待入库的诚信对象
	 * @param dataInfo
	 */
	private void getCXObject(List<String> dataInfo) {
		CXObject cxObject = new CXObject();
		if (dataInfo.size() < 4) {
			LOG.warn("该诚信数据【{}】异常，请核查！", dataInfo.toString());
			return;
		}
		String company = dataInfo.get(0).replace("(", "（").replace(")", "）");
		cxObject.setCompany(company);

		try {
			for (int i = 1; i < dataInfo.size(); i++) {
				String data = dataInfo.get(i);
				if (data.startsWith("统一信用代码") || data.startsWith("注册号")) {
					cxObject.setCreditcode(subFiledString(data));
					continue;
				} else if (data.startsWith("EPN")) {
					cxObject.setEpncode(subFiledString(data));
					continue;
				} else if (data.startsWith("法定代表人")) {
					cxObject.setLegalperson(subFiledString(data));
					continue;
				} else if (data.startsWith("注册资本")) {
					cxObject.setRegcapital(subFiledString(data));
					continue;
				} else if (data.startsWith("注册时间")) {
					String regdateStr = subFiledString(data);
					if (regdateStr.length() < 10) {
						LOG.error("注册时间字段数值不正确,【{}】", data);
						continue;
					}
					String dateStr = regdateStr.substring(0, 10);
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
					java.util.Date regDate = sdf.parse(dateStr);
					Date TimeStr = new java.sql.Date(regDate.getTime());
					cxObject.setRegdate(TimeStr);
					String state = regdateStr.replace(dateStr, "");
					cxObject.setState(state);
					continue;
				} else if (parseLevel(data)) {
					cxObject.setLvl(data);
					continue;
				} else {
					LOG.error("未知的数值字段,【{}】", data);
					return;
				}
			}
			cxList.add(cxObject);
		} catch (Exception e) {
			LOG.error("解析该诚信数据【{}】异常，请核查【{}】！", dataInfo.toString(), e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * 数据截取，截取"："后内容
	 * @param orString
	 * @return
	 */
	private String subFiledString(String orString) {
		return orString.substring(orString.indexOf("：") + 1);
	}

	/**
	 * 解析诚信等级，进行英文正则匹配
	 * @param lvlStr
	 * @return 
	 */
	private boolean parseLevel(String lvlStr) {
		Pattern pattern = Pattern.compile("[A-Z]+");
		Matcher m = pattern.matcher(lvlStr);
		return m.matches();
	}

	/**
	 * 进行批量入库操作
	 */
	private void doBitchInsert() {
		try {
			for (CXObject cxObject : cxList) {
				recordId++;
				stment.setInt(1, recordId);
				stment.setString(2, cxObject.getCompany());
				stment.setString(3, cxObject.getCreditcode());
				stment.setString(4, cxObject.getEpncode());
				stment.setString(5, cxObject.getLegalperson());
				stment.setString(6, cxObject.getRegcapital());
				stment.setDate(7, cxObject.getRegdate());
				stment.setString(8, cxObject.getState());
				stment.setString(9, cxObject.getLvl());
				stment.addBatch();
			}
			stment.executeBatch();
			conn.commit();
			LOG.info("已成功插入诚信数据【{}】", cxList.size());
		} catch (Exception e) {
			e.printStackTrace();
			try {
				conn.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}

	}

	/**
	 * 休憩片刻
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

/**
 * 诚信数据对象
 * @author shifeiyue
 *
 */
class CXObject {
	private String company;
	private String creditcode;
	private String epncode;
	private String legalperson;
	private String regcapital;
	private Date regdate;
	private String state;
	private String lvl;

	public CXObject() {

	}

	@Override
	public String toString() {
		return company + "," + creditcode + "," + epncode + "," + legalperson + "," + regcapital + "," + regdate + ","
				+ state + "," + lvl;
	}

	/**
	 * @return the company
	 */
	public String getCompany() {
		return company;
	}

	/**
	 * @param company
	 *            the company to set
	 */
	public void setCompany(String company) {
		this.company = company;
	}

	/**
	 * @return the creditcode
	 */
	public String getCreditcode() {
		return creditcode;
	}

	/**
	 * @param creditcode
	 *            the creditcode to set
	 */
	public void setCreditcode(String creditcode) {
		this.creditcode = creditcode;
	}

	/**
	 * @return the epncode
	 */
	public String getEpncode() {
		return epncode;
	}

	/**
	 * @param epncode
	 *            the epncode to set
	 */
	public void setEpncode(String epncode) {
		this.epncode = epncode;
	}

	/**
	 * @return the legalperson
	 */
	public String getLegalperson() {
		return legalperson;
	}

	/**
	 * @param legalperson
	 *            the legalperson to set
	 */
	public void setLegalperson(String legalperson) {
		this.legalperson = legalperson;
	}

	/**
	 * @return the regcapital
	 */
	public String getRegcapital() {
		return regcapital;
	}

	/**
	 * @param regcapital
	 *            the regcapital to set
	 */
	public void setRegcapital(String regcapital) {
		this.regcapital = regcapital;
	}

	/**
	 * @return the regdate
	 */
	public Date getRegdate() {
		return regdate;
	}

	/**
	 * @param regdate
	 *            the regdate to set
	 */
	public void setRegdate(Date regdate) {
		this.regdate = regdate;
	}

	/**
	 * @return the state
	 */
	public String getState() {
		return state;
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(String state) {
		this.state = state;
	}

	/**
	 * @return the lvl
	 */
	public String getLvl() {
		return lvl;
	}

	/**
	 * @param lvl
	 *            the lvl to set
	 */
	public void setLvl(String lvl) {
		this.lvl = lvl;
	}

}
