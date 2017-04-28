package com.focus.xd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import info.monitorenter.cpdetector.io.ASCIIDetector;
import info.monitorenter.cpdetector.io.CodepageDetectorProxy;
import info.monitorenter.cpdetector.io.JChardetFacade;
import info.monitorenter.cpdetector.io.ParsingDetector;
import info.monitorenter.cpdetector.io.UnicodeDetector;

/**
 * 本地数据读取类
 * @author shifeiyue
 *
 */
public class ReadLocalTxt {	
	
	/**
	 * 获取本地的txt文件内容
	 * @param filePath
	 * @return
	 */
	public static List<String> getLocalTxt(String filePath) {
		List<String> linkList = new ArrayList<String>();
		FileInputStream fis = null;
		BufferedReader reader = null;
		try {
			fis = new FileInputStream(filePath);
			reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			String lineContent = null;
			while ((lineContent = reader.readLine()) != null) {
				lineContent = lineContent.trim();
				if (lineContent.equals("")) {
					continue;
				}
				if (!linkList.contains(lineContent))
					linkList.add(lineContent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
				if (fis != null)
					reader.close();
			} catch (Exception localException2) {
			}
		}
		return linkList;
	}
	
	/**
	 * 获取诚信数据文件内容
	 * 1. 首先获取文件的编解码
	 * @param filePath
	 * @return 
	 */
	public static List<String> getCXTxt(String filePath) {
		List<String> cotentList = new ArrayList<String>();
		String coding = getFileEncode(filePath);
		if (coding==null) {
			System.out.println("未能获取文件的编码格式!文件全路径:" + filePath);
			return cotentList;
		}

		if (!coding.equals("GB2312") && !coding.equals("UTF-8")&& !coding.equals("GB18030")) {
			System.out.println("未知的编码格式:"+ coding);
			System.out.println("文件全路径:" + filePath);
			return cotentList;
		}

		FileInputStream fis = null;
		BufferedReader reader = null;
		try {
			fis = new FileInputStream(filePath);
			reader = new BufferedReader(new InputStreamReader(fis, coding));
			String lineContent = null;
			while ((lineContent = reader.readLine()) != null) {
				lineContent = lineContent.trim();
				if (lineContent.equals("") || lineContent.equals("申请查看报告") 
						|| lineContent.equals("订阅信用动态")|| lineContent.startsWith("诚信等级为") ) {
					continue;
				}
				if (!cotentList.contains(lineContent))
					cotentList.add(lineContent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
				if (fis != null)
					reader.close();
			} catch (Exception localException2) {
			}
		}
		return cotentList;
	}
	
	/**
     * 利用第三方开源包cpdetector获取文件编码格式
     * 
     * @param path
     *            要判断文件编码格式的源文件的路径
     */
    private static String getFileEncode(String path) {
        /*
         * detector是探测器，它把探测任务交给具体的探测实现类的实例完成。
         * cpDetector内置了一些常用的探测实现类，这些探测实现类的实例可以通过add方法 加进来，如ParsingDetector、
         * JChardetFacade、ASCIIDetector、UnicodeDetector。
         * detector按照“谁最先返回非空的探测结果，就以该结果为准”的原则返回探测到的
         * 字符集编码。使用需要用到三个第三方JAR包：antlr.jar、chardet.jar和cpdetector.jar
         * cpDetector是基于统计学原理的，不保证完全正确。
         */
        CodepageDetectorProxy detector = CodepageDetectorProxy.getInstance();
        /*
         * ParsingDetector可用于检查HTML、XML等文件或字符流的编码,构造方法中的参数用于
         * 指示是否显示探测过程的详细信息，为false不显示。
         */
        detector.add(new ParsingDetector(false));
        /*
         * JChardetFacade封装了由Mozilla组织提供的JChardet，它可以完成大多数文件的编码
         * 测定。所以，一般有了这个探测器就可满足大多数项目的要求，如果你还不放心，可以
         * 再多加几个探测器，比如下面的ASCIIDetector、UnicodeDetector等。
         */
        detector.add(JChardetFacade.getInstance());// 用到antlr.jar、chardet.jar
        // ASCIIDetector用于ASCII编码测定
        detector.add(ASCIIDetector.getInstance());
        // UnicodeDetector用于Unicode家族编码的测定
        detector.add(UnicodeDetector.getInstance());
        java.nio.charset.Charset charset = null;
        File f = new File(path);
        try {
            charset = detector.detectCodepage(f.toURI().toURL());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (charset != null)
            return charset.name();
        else
            return null;
    }
}