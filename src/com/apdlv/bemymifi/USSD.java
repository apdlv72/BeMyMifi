package com.apdlv.bemymifi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.util.Log;

class USSD {
    
    private static String startmsg="displayMMIComplete"; //start msg to look for 
    private static String endmsg="MMI code has finished running"; //stop msg 
    private static String trimmsg="- using text from MMI message: '"; //a msg to remove from the text
    
    private long before=3000; //delay (ms) before creation of the class before a msg (USDD) is valid (use timestamp)
    private long after=3000;  //delay (ms) after creation of the class that a msg (USDD) is valid (wait after ms) 
    
    private String msg=""; //the USSD message
    private boolean found=false;
    private long t=-1; //timestamp of the found log
    private MainActivity mainActivity;
    
    
    public USSD(MainActivity mainActivity)
    {
            this(3000,3000);
            this.mainActivity = mainActivity;
    }
    
    //USSD in log : example 
    public USSD(long before_creation,long after_creation)
    {
            before=before_creation;
            after=after_creation;
            long timestamp=System.currentTimeMillis(); //creation of the class --> look for the USSD msg in the logs
            Log.d("USSDClass", "Class creation - timestamp: "+String.valueOf(timestamp));
            try { 
                    //sample code taken from alogcat ...
                     Process logcatProc = Runtime.getRuntime().exec("logcat -v time -b main PhoneUtils:D"); //get PhoneUtils debug log with time information
                     BufferedReader mReader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), 1024);
                     String line="";                         
                     boolean tostop=false;                  
                     long stop=timestamp+after; //to stop the while after "after" ms 
                     while (((line = mReader.readLine()) != null)&&(System.currentTimeMillis()<stop)&&(tostop==false)) {
                             if (line.length()>19) //the line should be at least with a length of a timestamp (19) !
                             {
                                     if (line.contains(startmsg)) //check if it is a USSD msg
                                     {
                                            //log example : "12-10 20:36:39.321 D/PhoneUtils(  178): displayMMIComplete: state=COMPLETE"
                                            t=extracttimestamp(line); //extract the timestamp of thie msg
                                            Log.d("USSDClass", "Found line at timestamp : "+String.valueOf(t));
                                            if (t>=timestamp-before) found=true; //start of an USDD is found & is recent !
                                     }
                                     else if (found) {
                                             //log example : "12-10 20:36:39.321 D/PhoneUtils(  178): displayMMIComplete: state=COMPLETE"
                                             if (line.contains(endmsg)) tostop=true;
                                             else {
                                                     //log example : "12-10 20:36:39.321 D/PhoneUtils(  178): - using text from MMI message: 'Your USSD message with one or several lines"
                                                     Log.d("USSDClass", "Line content : "+line);
                                                     String[] v=line.split("\\): "); //doesn't need log information --> split with "): " separator
                                                     if (v.length>1)        msg+=v[1].replace(trimmsg, "").trim()+"\n";
                                                     
                                                     mainActivity.setCredit("line");
                                                             
                                             }
                                     }
                             }      
                     }
            } catch (IOException e) {
                    Log.d("USSDClass", "Exception:"+e.toString());                          
            }                                       
    }
    
    public boolean IsFound()
    {
            return found;
    }
    
    public String getMsg()
    {               
            return msg;     
    }
    
    //extract timestamp from a log line with format "MM-dd HH:mm:ss.ms Level/App:msg"  Example : 12-10 20:36:39.321
    //Note : known bug : happy new year check will not work !!!
    private long extracttimestamp(String line)
    {       
            long timestamp=-1; //default value if no timestamp is found
            String[] v=line.split(" ");             
            if (v.length>1) //check if there is space
            {
                    Calendar C=Calendar.getInstance();
                    int y=C.get(Calendar.YEAR);                 
                String txt=v[0]+"-"+y+" "+v[1]; //transform in format "MM-dd-yyyy HH:mm:ss"  
                SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
            try {           
                    Date tmp=formatter.parse(txt);          
                    timestamp=tmp.getTime();
                    String[] ms=v[1].split("."); //get ms
                    if (ms.length>1) timestamp+=Integer.getInteger(ms[1]);
                    
            } catch (ParseException e)
            {
                    Log.d("USSDClass", "USDD.extractimestamp exception:"+e.toString());
            }                   
            }
            return timestamp;
            
    }
    
    
}