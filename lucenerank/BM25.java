  /*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//package org.apache.lucene.demo;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.BooleanQuery;


/** Simple command-line based search demo. */
public class BM25 {

  private BM25() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = args[0];
    String field = "contents";
    String queries = args[1];
    int repeat = 0;
    boolean raw = false;
    String queryString = null;
    int hitsPerPage = 1000;
    
    // for(int i = 0;i < args.length;i++) {
    //   if ("-index".equals(args[i])) {
    //     index = args[i+1];
    //     i++;
    //   } else if ("-field".equals(args[i])) {
    //     field = args[i+1];
    //     i++;
    //   } else if ("-queries".equals(args[i])) {
    //     queries = args[i+1];
    //     i++;
    //   } else if ("-query".equals(args[i])) {
    //     queryString = args[i+1];
    //     i++;
    //   } else if ("-repeat".equals(args[i])) {
    //     repeat = Integer.parseInt(args[i+1]);
    //     i++;
    //   } else if ("-raw".equals(args[i])) {
    //     raw = true;
    //   } else if ("-paging".equals(args[i])) {
    //     hitsPerPage = Integer.parseInt(args[i+1]);
    //     if (hitsPerPage <= 0) {
    //       System.err.println("There must be at least 1 hit per page.");
    //       System.exit(1);
    //     }
    //     i++;
    //   }
    // }
    
    //System.out.println(Paths.get(index));
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(new BM25Similarity(1.4f,0.9f));
    //searcher.setSimilarity(new LMDirichletSimilarity());
    //searcher.setSimilarity(new LMJelinekMercerSimilarity(0.1f));
    Analyzer analyzer = new StandardAnalyzer();

    BufferedReader in = null;
    if (queries != null) {
      in = Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8);
    } else {
      in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }
    QueryParser parser = new QueryParser(field, analyzer);
    while (true) {
      if (queries == null && queryString == null) {
        //System.out.println(field);       // prompt the user
        System.out.println("Enter query: ");
      }

      String line = queryString != null ? queryString : in.readLine();

      if (line == null || line.length() == -1) {
        break;
      }

      line = line.trim();
      if (line.length() == 0) {
        break;
      }
      
      Query query = parser.parse(line);
      //System.out.println(query.toString(field));
      System.out.println("Searching for: " + query.toString(field));
            
      if (repeat > 0) {                           // repeat & time as benchmark
        Date start = new Date();
        for (int i = 0; i < repeat; i++) {
          searcher.search(query, 100);
        }
        Date end = new Date();
        System.out.println("Time: "+(end.getTime()-start.getTime())+"ms");
      }

      String filename = args[2];
      String filename1 = args[3];
      doPagingSearch(in, searcher, query, filename, filename1, hitsPerPage, raw, queries == null && queryString == null);

      if (queryString != null) {
        break;
      }
    }
    reader.close();
  }

  /**
   * This demonstrates a typical paging search scenario, where the search engine presents 
   * pages of size n to the user. The user can then go to the next page if interested in
   * the next hits.
   * 
   * When the query is executed for the first time, then only enough results are collected
   * to fill 5 result pages. If the user wants to page beyond this limit, then the query
   * is executed another time and all hits are collected.
   * 
   */
  public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, String filename, String filename1,
                                     int hitsPerPage, boolean raw, boolean interactive) throws IOException {
    //System.out.println(query.toString("contents"));
    // Collect enough docs to show 5 pages
    TopDocs results = searcher.search(query, hitsPerPage);
    ScoreDoc[] hits = results.scoreDocs;
    
    //System.out.println("+++++++++++" + hits[0]);
    
    int numTotalHits = Math.toIntExact(results.totalHits.value);
    System.out.println(numTotalHits + " total matching documents");

    int start = 0;
    int end = Math.min(numTotalHits, hitsPerPage);
        
    while (true) {
      if (end > hits.length) {
        System.out.println("Only results 1 - " + hits.length +" of " + numTotalHits + " total matching documents collected.");
        System.out.println("Collect more (y/n) ?");
        String line = in.readLine();
        if (line.length() == 0 || line.charAt(0) == 'n') {
          break;
        }

        hits = searcher.search(query, numTotalHits).scoreDocs;
      }
      
      end = Math.min(hits.length, start + hitsPerPage);
      
      FileWriter writer1=new FileWriter(filename1,true);
      writer1.write("<top>" + "\n" + "<num>" + " ");
      writer1.close();
      //int k=0;

      for (int i = start; i < end; i++) {
        if (raw) {                              // output raw format
          System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
          continue;
        }

        Document doc = searcher.doc(hits[i].doc);
        String path = doc.get("path");
        if (path != null) {
          //System.out.println(hits[i]);
          //System.out.println((i+1) + ". " + path + " score="+hits[i].score);
          //System.out.println(hits[i]);
          String str = "";
          for(int j = 0; j < query.toString("contents").length()-1; j++)
          {
            str = str + query.toString("contents").charAt(j);
            //System.out.println(query.toString("contents").charAt(j));
            if(query.toString("contents").charAt(j+1) == ' '){
              break;
            }
          }
          
          if(i==start){
          FileWriter writer2=new FileWriter(filename1,true);
          writer2.write(str + "\n" + "<title>" + " " + query.toString("contents") + "\n");
          writer2.close();
          }
          
          String docid = doc.get("docid");
          //System.out.println(doc);
          //System.out.println("++++++++++++++++++++++++++++++\n");
          if(i>=0 && i<20){
            FileWriter writer3=new FileWriter(filename1,true);
            writer3.write("<relevant>" + " " + doc.get("contents"));
            writer3.close();

          }
          if(i>=980 && i<1000){
            FileWriter writer4=new FileWriter(filename1,true);
            writer4.write("<irrelevant>" + " " + doc.get("contents"));
            writer4.close();
          }
          if(i==99){
            FileWriter writer5=new FileWriter(filename1,true);
            writer5.write("</top>");
            writer5.close();
          }

          String doc_id = doc.get("initial_path");
          String docpath = doc.get("docpath");
          String url = ',' + doc.get("url");
          String journal = ',' + doc.get("journal");
          String date = ',' + doc.get("date");

          
          String strr = str + "  " + "Q0" + "  " + doc_id + "  " + docpath + ' ' + url + ' ' + journal + ' ' + date + ' ' + (i) + "  " + hits[i].score + "  " + "Yibo_Wang";
          FileWriter writer=new FileWriter(filename,true);
          SimpleDateFormat format=new SimpleDateFormat();
          String time=format.format(new Date());
          writer.write(strr+"\n");
          writer.close();

          //System.out.println(str);
          //System.out.println(str + "\t" + "Q0" + "\t" + path + "\t" + (i+1) + "\t" + hits[i].score + " \t" + "Yao & Yibo");
          String title = doc.get("title");
          if (title != null) {
            System.out.println("   Title: " + doc.get("title"));
          }
        } else {
          System.out.println((i+1) + ". " + "No path for this document");
        }
                  
      }

      if (!interactive || end == 0) {
        break;
      }

      if (numTotalHits >= end) {
        boolean quit = false;
        while (true) {
          System.out.print("Press ");
          if (start - hitsPerPage >= 0) {
            System.out.print("(p)revious page, ");  
          }
          if (start + hitsPerPage < numTotalHits) {
            System.out.print("(n)ext page, ");
          }
          System.out.println("(q)uit or enter number to jump to a page.");
          
          String line = in.readLine();
          if (line.length() == 0 || line.charAt(0)=='q') {
            quit = true;
            break;
          }
          if (line.charAt(0) == 'p') {
            start = Math.max(0, start - hitsPerPage);
            break;
          } else if (line.charAt(0) == 'n') {
            if (start + hitsPerPage < numTotalHits) {
              start+=hitsPerPage;
            }
            break;
          } else {
            int page = Integer.parseInt(line);
            if ((page - 1) * hitsPerPage < numTotalHits) {
              start = (page - 1) * hitsPerPage;
              break;
            } else {
              System.out.println("No such page");
            }
          }
        }
        if (quit) break;
        end = Math.min(numTotalHits, start + hitsPerPage);
      }
    }
  }
}