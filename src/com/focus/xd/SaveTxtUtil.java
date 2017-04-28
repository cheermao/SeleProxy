package com.focus.xd;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据存储工具
 *
 */
public class SaveTxtUtil
{
  private static Logger LOG = LoggerFactory.getLogger(SaveTxtUtil.class);

  public static void createLocalFolder(String idString) {
    try {
      Files.createDirectories(Paths.get(idString, new String[0]), new FileAttribute[0]);
    } catch (Exception e) {
      e.printStackTrace();
      LOG.error("Error creating positionFile parent directories", e);
    }
  }

  /**
   * 保存数据至本地文件
   * @param fileName
   * @param content
   * @return
   */
  public static boolean saveTextToFile(String fileName, String content) {
    try {
      String fileNameTemp = fileName;
      File filePath = new File(fileNameTemp);
      if (!filePath.exists())
        filePath.createNewFile();
      else {
        filePath.delete();
      }
      FileWriter fw = new FileWriter(filePath);
      PrintWriter pw = new PrintWriter(fw);
      pw.println(content);
      pw.flush();
      pw.close();
      fw.close();
      return true;
    } catch (Exception e) {
      LOG.error("保存文本信息至文件发生未知错误: " + e.getMessage());
    }
    return false;
  }

  /**
   * 追加数据至文件，并追加换行符
   * @param filepath
   * @param content
   */
  public static void addContentToFile(String filepath, String content)
  {
    RandomAccessFile bakraf = null;
    try {
      bakraf = new RandomAccessFile(filepath, "rw");
      long fileLength = bakraf.length();
      bakraf.seek(fileLength);
      byte[] contBytes = content.getBytes("UTF-8");
      bakraf.write(contBytes);
      bakraf.write("\r\n".getBytes());
    } catch (Exception e) {
      LOG.error("追加信息至文件发生未知错误: " + e.getMessage());
    }
    finally
    {
      if (bakraf != null)
        try {
          bakraf.close();
        }
        catch (Exception e)
        {
        }
    }
  }
}