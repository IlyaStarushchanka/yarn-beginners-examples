/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wikibooks.hadoop.yarn.examples;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.AllocateResponse;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Records;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static java.util.Collections.reverseOrder;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class HelloYarn {
  private static final long MEGABYTE = 1024L * 1024L;
  private int containerCount;
  private int tempContainer;

  public HelloYarn() {
    System.out.println("HelloYarn!");
  }

  public HelloYarn(int tempContainer, int containerCount) {
    this.tempContainer = tempContainer;
    this.containerCount = containerCount;
    System.out.println("HelloYarn!");
  }

  public static long bytesToMegabytes(long bytes) {
    return bytes / MEGABYTE;
  }

  public void printMemoryStats() {
    /*long freeMemory = bytesToMegabytes(Runtime.getRuntime().freeMemory());
    long totalMemory = bytesToMegabytes(Runtime.getRuntime().totalMemory());
    long maxMemory = bytesToMegabytes(Runtime.getRuntime().maxMemory());

    System.out.println("The amount of free memory in the Java Virtual Machine: " + freeMemory);
    System.out.println("The total amount of memory in the Java virtual machine: " + totalMemory);
    System.out.println("The maximum amount of memory that the Java virtual machine: " + maxMemory);*/
    try{
      Pattern p = Pattern.compile("http[s]*:[^\\s\\r\\n]+");
      List<String> urls = new ArrayList<String>();

      Path pt=new Path(Constants.INPUT_FILE);

      FileSystem fs2 = FileSystem.get(new Configuration());
      Configuration conf = new Configuration();
      conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
      conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

      FileSystem fs = FileSystem.get(new URI("hdfs://sandbox.hortonworks.com:8020"),conf);
      BufferedReader br=new BufferedReader(new InputStreamReader(fs.open(pt)));
      List<String> lines = new ArrayList<String>();

      String line=br.readLine();
      String topLine = line;
      line=br.readLine();
      while (line != null){
        lines.add(line.trim());
        line=br.readLine();
      }

      int offset, count;
      if (tempContainer < containerCount){
        count = Math.round(lines.size()/containerCount);
      } else {
        count = lines.size() - Math.round(lines.size()/containerCount)*(containerCount - 1);
      }

      offset = Math.round(lines.size()/containerCount)*(tempContainer - 1);
      System.out.println("count = " + count);
      System.out.println("offset = " + offset);
      System.out.println("STEP 1 " + lines.size());
      /*for (String l : lines) {
        Matcher m = p.matcher(l);
        m.matches();
        while (m.find()) {
          urls.add(m.group());
        }
      }*/
      for (int i = offset; i <= offset + count; i++){
        String l = lines.get(i);
        Matcher m = p.matcher(l);
        m.matches();
        while (m.find()) {
          urls.add(m.group());
        }
      }
      //List<String> urls = getUrlsFromDB();
      System.out.println("STEP 2 " +urls.size());
      List<List<String>> totalTopWords = new ArrayList<>();
      for (String u : urls) {
        Document d = Jsoup.connect(u).get();
        String text = d.body().text();

        StringTokenizer tokenizer = new StringTokenizer(text, " .,?!:;()<>[]\b\t\n\f\r\"\'\\");
        List<String> words = new ArrayList<String>();
        while(tokenizer.hasMoreTokens()) {
          words.add(tokenizer.nextToken());
//System.out.println(tokenizer.nextToken());
        }

        List<String> topWords = words.stream()
                .map(String::toLowerCase)
                .collect(groupingBy(Function.identity(), counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long> comparingByValue(reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(toList());
        totalTopWords.add(topWords);
      }

      System.out.println("STEP 3 " +totalTopWords.size());
      try{
        Path ptOut=new Path(Constants.OUTPUT_FILE + "part" + tempContainer + ".txt");
        //Configuration conf = new Configuration();
        conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
        conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

        FileSystem fsOut = FileSystem.get(new URI("hdfs://sandbox.hortonworks.com:8020"),conf);
        //FileSystem fsOut = FileSystem.get(new Configuration());
        BufferedWriter brOut = new BufferedWriter(new OutputStreamWriter(fsOut.create(ptOut,true)));

        brOut.write(topLine);
        brOut.write("\n");
        System.out.println("STEP 4");
        for (int i = 0; i < lines.size(); i++) {
          String currentLine = lines.get(i);
          String[] params = currentLine.split("\\s+");
          for (int j = 0; j < params.length; j++) {
            if (j == 1) {
              List<String> currentTopWords = totalTopWords.get(i);

              for (int k = 0; k < currentTopWords.size(); k++) {
                brOut.write(currentTopWords.get(k));
                if (k < (currentTopWords.size()-1)) {
                  brOut.write(",");
                }
              }
              brOut.write(" ");
            }
            brOut.write(params[j]);
            if (j < (params.length-1)) {
              brOut.write(" ");
            }
          }
          brOut.write("\n");
        }

        System.out.println("STEP 5");
        brOut.close();
      }catch(Exception e) {
        System.out.println(e.getMessage());
      }
    }catch(Exception e){
      System.out.println(e.getMessage());
    }
  }

  public static void main(String[] args) {
    String tempContainer = args[0];
    System.out.println(tempContainer);
    String containerCount = args[1];
    System.out.println(containerCount);
    HelloYarn helloYarn = new HelloYarn(Integer.valueOf(tempContainer), Integer.valueOf(containerCount));

    helloYarn.printMemoryStats();
  }
}
