package com.apdlv.bemymifi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.apdlv.bemifi.R;


public class MainActivity extends Activity implements OnClickListener
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
	
	mLayout = (RelativeLayout) findViewById(R.id.topLayout);
	
	(mButtonEnableMifi  = (Button) findViewById(R.id.buttonEnableMifi)).setTextColor(Color.rgb(0,80,0));
	(mButtonDisableMifi = (Button)findViewById(R.id.buttonDisableMifi)).setTextColor(Color.rgb(80,0,0));
	mButtonCheckCredits = (Button) findViewById(R.id.buttonCheckCredits);
	
	mButtonEnableMifi.setOnClickListener(this);
	mButtonDisableMifi.setOnClickListener(this);
	mButtonCheckCredits.setOnClickListener(this);
	
	mTextviewMobileData  = (TextView) findViewById(R.id.textViewMobileData);
	mTextviewAccessPoint = (TextView) findViewById(R.id.textViewAccessPoint);
	mTextviewTraffic    = (TextView) findViewById(R.id.textViewTraffic); //.setTextColor(Color.BLACK);
	mTextviewClients    = (TextView) findViewById(R.id.textViewClients); //.setTextColor(Color.BLACK);

	IntentFilter intentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
	intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
	registerReceiver(networkStateReceiver, intentFilter);
	
	WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
	
	mSavedWifiStatus = wifiManager.isWifiEnabled();	
	mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	
	mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
	mAudioManager.loadSoundEffects();
        //mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        //Settings.System.putInt(this.getContentResolver(), SOUND_EFFECTS_ENABLED, 1);
	
	int streams[]  = 
	{ 
		AudioManager.STREAM_SYSTEM, 
		AudioManager.STREAM_NOTIFICATION, 
		AudioManager.STREAM_MUSIC, 
		AudioManager.STREAM_SYSTEM, 
	};
	
	for (int stream : streams)
	{
            int curvol  = 0;
            int prevvol = 0;
            do 
            {
                prevvol = curvol;
                mAudioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0);
                curvol = mAudioManager.getStreamVolume(stream);
            }
            while (curvol != prevvol);        
	}
    }
    
    
    @Override
    protected void onStart() 
    {
	super.onStart();
	mStatsThread = new StatsThread();
	mStatsThread.start();
    };
    
    
    @Override
    protected void onDestroy() 
    {
	super.onDestroy();
	mStatsThread.mContinue = false;
	// simulate a click on disable button
	onClick(mButtonDisableMifi);
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	// Inflate the menu; this adds items to the action bar if it is present.
	//getMenuInflater().inflate(R.menu.main, menu);
	return false;
    }

    
    @Override
    public void onClick(View v)
    {
	try
	{
	    if (v==mButtonEnableMifi)
	    {
		setWifiTetheringEnabled(true);
		setMobileDataEnabled(this, true);
		//playSound(MP3_TICK);
	    }
	    else if (v==mButtonDisableMifi)
	    {
		setWifiTetheringEnabled(false);
		setMobileDataEnabled(this, false);
		//playSound(MP3_TICK);
	    }
	    else if (v==mButtonCheckCredits)
	    {
		requestAccountCredits();
	    }
	}
	catch (Exception e)
	{
	    Log.e(TAG, "Exception: " + e);
	}		
    }


    public void setCredit(String string)
    {
        //mTextviewCredits.setText(string);
    }


    private class RxTxStats
    {
        private long mRx;
        private long mTx;
        @SuppressWarnings("unused")
        private long tRx;
        @SuppressWarnings("unused")
        private long tTx;
    
        public RxTxStats(long mRx, long mTx, long tRx, long tTx)
        {
            this.mRx = mRx; 
            this.mTx = mTx;
            this.tRx = tRx;
            this.tTx = tTx;
        }
    }

    private class StatsThread extends Thread
    {
        public boolean mContinue = true;
        private long mLastLeasesRead = 0;
        
        @Override
        public void run()
        {
            String clientsOld = "";
            while (mContinue)
            {
        	long mobileRx = TrafficStats.getMobileRxBytes();
        	long mobileTx = TrafficStats.getMobileTxBytes();	    
        	long totalRx  = TrafficStats.getTotalRxBytes();
        	long totalTx  = TrafficStats.getTotalTxBytes();
    
        	//Log.d(TAG, "mobile: " + mobileRx + "/" + mobileTx);
        	//Log.d(TAG, "total:  " + totalRx  + "/" + totalTx);
    
        	RxTxStats stats = new RxTxStats(mobileRx, mobileTx, totalRx, totalTx);
        	Message msg = mHandler.obtainMessage(MSG_TRAFFIC, stats);
        	msg.sendToTarget();
    
        	String clients = findDhcpClients();
        	if (!clientsOld.matches(clients))
        	{
        	    msg = mHandler.obtainMessage(MSG_CLIENTS, clients);
        	    msg.sendToTarget();		    
        	}
        	clientsOld = clients;
    
        	try { Thread.sleep(500); } catch (InterruptedException e) {}		
            }
        }
        
        
        protected String findClientName(String ip)
        {
            BufferedReader br = null;
    
            if (null!=mIpToNameMap && null!=mIpToNameMap.get(ip))
            {
        	return mIpToNameMap.get(ip);
            }
    
            mIpToNameMap = new HashMap<String, String>();
            
            long now = Calendar.getInstance().getTimeInMillis();
            if (now-mLastLeasesRead<5000)
            {
        	return null;
            }
            mLastLeasesRead = now;
            
            try 
            {		
        	br = new BufferedReader(new FileReader("/data/misc/dhcp/dnsmasq.leases"));
            }
            catch (FileNotFoundException e)
            {
        	System.out.println("" + e);
            }
    
            if (null==br)
            {
                Process ps;
                try
                {
                    String args[] = { "su", "-c", "cat /data/misc/dhcp/dnsmasq.leases" };
                    ps = Runtime.getRuntime().exec(args);
                    BufferedReader err = new BufferedReader(new InputStreamReader(ps.getErrorStream()));
                    String line = err.readLine();
                    while (null!=line)
                    {
                	System.err.println(line);
                	line = err.readLine();
                    }	            
                    
                    br = new BufferedReader(new InputStreamReader(ps.getInputStream()));
                } 
                catch (IOException e)
                {
                    e.printStackTrace();
                } 
            }	    
    
            if (null==br)
            {
        	return mIpToNameMap.get(ip);
            }
            
            try 
            {		
        	//br = new BufferedReader(new FileReader("/data/misc/dhcp/dnsmasq.leases"));
        	String line = br.readLine();
        	while (null!=line) 
        	{
        	    String[] splitted = line.split(" +");
        	    if (splitted != null && splitted.length >= 4)
        	    {
//        		String time = splitted[0];
//        		String mac1 = splitted[1];
        		String addr   = splitted[2];
        		String name = splitted[3];
        		
        		mIpToNameMap.put(addr, name);
        	    }
        	    line = br.readLine();
        	}
            }
            catch (Exception e) 
            { 
        	e.printStackTrace();
            } 
            finally 
            {
        	try 
        	{
        	    if (null!=br) br.close();
        	} 
        	catch (IOException e) 
        	{
        	    e.printStackTrace();
        	}
            }	
            
            System.out.println("Leases: " + mIpToNameMap);
            return mIpToNameMap.get(ip);
        }
        
        Map<String, String> mIpToNameMap = null; 
        
        protected String findDhcpClients()
        {
            StringBuilder sb = new StringBuilder();
            //int macCount = 0;
            BufferedReader br = null;
            try 
            {
        	br = new BufferedReader(new FileReader("/proc/net/arp"));
        	String line;
        	while ((line = br.readLine()) != null) 
        	{
        	    String[] splitted = line.split(" +");
        	    if (splitted != null && splitted.length >= 4) 
        	    {
        		String mac = splitted[3]; 			
        		String ip  = splitted[0];
        		
        		if (mac.matches("..:..:..:..:..:..")) 
        		{
        		    //macCount++;
        		    
        		    String name = findClientName(ip);			   
        		    if (null==name)
        		    {
        			sb.append(ip).append('\n');
        		    }
        		    else
        		    {
        			sb.append(name).append(' ').append(ip).append('\n');
        		    }
        		    
        		    Log.d(TAG, "Found new MAC: " + mac);
        		} 
        	    }
        	}
            } 
            catch (Exception e) 
            { 
        	e.printStackTrace();
            } 
            finally 
            {
        	try 
        	{
        	    if (null!=br) br.close();
        	} 
        	catch (IOException e) 
        	{
        	    e.printStackTrace();
        	}
            }	
    
            return sb.toString();
        }
    
    }

    private Boolean mLastMifiEnabled = null;
    
    private BroadcastReceiver networkStateReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d(TAG, "Connectivity changed");
            boolean tetheringEnabled = isWifiTetheringEnabled();
                        
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);	    
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    
            boolean mobileDataEnabled = false;	    
            if (null!=activeNetwork && ConnectivityManager.TYPE_MOBILE==activeNetwork.getType())
            {
                mobileDataEnabled=true;
            }
            
            mTextviewMobileData.setText("Mobile data: " + (mobileDataEnabled ? "ENABLED" : "off"));
            mTextviewMobileData.setTextColor(Color.WHITE);
    
            boolean mifiEnabled = tetheringEnabled && mobileDataEnabled;	    
            mLayout.setBackgroundColor(mifiEnabled ? Color.rgb(0,80,0) : Color.rgb(80,0,0));
            
            // disable screen saver if mifi is to keep user aware of possible costs  
            if (mifiEnabled)
            {
        	getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);		
            }
            else
            {
        	getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
                        
            // play a sound if the mifi status changed
            if (null!=mLastMifiEnabled && mLastMifiEnabled!=mifiEnabled)
            {
        	playSound(mifiEnabled ? MP3_ENABLED : MP3_DISABLED);
            }
            mLastMifiEnabled = mifiEnabled;
                        
            final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] infos = conman.getAllNetworkInfo();
            for (NetworkInfo info : infos)
            {
        	Log.d(TAG, "INFO: " + info);
            }
        }
    };

    private Handler mHandler = new Handler()
    {
        final long THRESHOLD = 1024*1024; // vibrate every megabyte
        private long mLastBytes = -1;
        
        @Override
        public void handleMessage(android.os.Message msg) 
        {
            int what = msg.what;
            if (MSG_TRAFFIC==what)
            {
        	RxTxStats stats = (RxTxStats) msg.obj;		
        	mTextviewTraffic.setText(stats.mTx + " / " + stats.mRx + " bytes");
        	
        	long sum = stats.mRx+stats.mTx;
        	if (sum-mLastBytes>=THRESHOLD)
        	{
        	    mLastBytes = sum;
        	    mVibrator.vibrate(100);
        	    playSound(MP3_CONNECTION);
        	}
        	else if (0==sum)
        	{
        	    mLastBytes = 0;
        	}
            }
            if (MSG_CLIENTS==what)
            {
        	String clients = (String)msg.obj;
        	mTextviewClients.setText(clients);
            }
        };
    };

    
//    private void clickSound()
//    {
//        if (mWithSound) mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
//    }

    
    private void playSound(int mp3ResID)
    {
        if (!mWithSound) return;
        
        MediaPlayer mp = MediaPlayer.create(this, mp3ResID); //MP3_CLOCKTICK1);
        mp.start();
    }

    private boolean isWifiTetheringEnabled()
    {
	Boolean tetheringEnabled = null;
	WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
	
	try
        {	    
	    // 03-24 15:52:10.679: D/MainActivity(3970): found method: public boolean android.net.wifi.WifiManager.isWifiApEnabled()
	    Method isWifiApEnabledConfig = wifiManager.getClass().getMethod("isWifiApEnabled", null);
	    tetheringEnabled = (Boolean) isWifiApEnabledConfig.invoke(wifiManager);
	    
	    //Method getWifiConfig = wifiManager.getClass().getMethod("getWifiApConfiguration", (Class<?>)null);
	    //WifiConfiguration myConfig = (WifiConfiguration) getWifiConfig.invoke(wifiManager, (Class<?>)null);
	    
	    //System.out.println("getWifiConfig : " + getWifiConfig);
	    //System.out.println("myConfig : " + myConfig);
	    System.out.println("tetheringEnabled : " + tetheringEnabled);
	    	    
	    mTextviewAccessPoint.setText("Access point: " + (tetheringEnabled ? "ENABLED" : "off"));
	    mTextviewAccessPoint.setTextColor(Color.WHITE);
        } 
	catch (Exception e)
        {
	    e.printStackTrace();
        }

	/*
	Method[] methods = wifiManager.getClass().getDeclaredMethods();
	for (Method method : methods) 
	{
	    Log.d(TAG, "found method: " + method);
	}
	*/
	
	return null!=tetheringEnabled && true==tetheringEnabled;
    }
    
    
    private void setMobileDataEnabled(Context context, boolean enabled) throws ClassNotFoundException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InvocationTargetException 
    {
	    final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    final Class<?> conmanClass = Class.forName(conman.getClass().getName());
	    final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
	    iConnectivityManagerField.setAccessible(true);
	    
	    final Object iConnectivityManager = iConnectivityManagerField.get(conman);
	    final Class<?> iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
	    final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
	    setMobileDataEnabledMethod.setAccessible(true);

	    setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);	    
	    
	}
    
    
    private void setWifiTetheringEnabled(boolean enable) 
    {
	WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
	
	List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();	
	if (null!=networks) for (WifiConfiguration network : networks)
	{
	    Log.d(TAG, "NETWORK: " + network);
	}
	
	List<ScanResult> scanResults = wifiManager.getScanResults();
	if (null!=scanResults) for (ScanResult scanResult : scanResults)
	{
	    Log.d(TAG, "SCANRESULT" + scanResult);
	}

	DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
	Log.d(TAG, "DHCPINFO: " + dhcpInfo);
	
	WifiInfo connInfo = wifiManager.getConnectionInfo();
	Log.d(TAG, "CONNINFO: " + connInfo);
	
	
	if (enable)
	{
	    mSavedWifiStatus = wifiManager.isWifiEnabled();
	    wifiManager.setWifiEnabled(false);
	}

	Method[] methods = wifiManager.getClass().getDeclaredMethods();
	for (Method method : methods) 
	{
	    Log.d(TAG, "found method: " + method);
	    if (method.getName().equals("setWifiApEnabled")) 
	    {
		try 
		{
		    Method setWifiApEnabled = method;
		    // do not pass netconfig because this will override e.g. security settings, pre shared key etc.
//		    WifiConfiguration netConfig = new WifiConfiguration();
//		    netConfig.SSID = "BertasAP";
		    		    
		    @SuppressWarnings("unused") // probably a Boolean
                    Object rc = setWifiApEnabled.invoke(wifiManager, null /*netConfig*/, enable);
		} 
		catch (Exception ex) 
		{
		    System.err.println("" + ex);
		}
		break;
	    }
	}
	
	if (!enable)
	{
	    wifiManager.setWifiEnabled(mSavedWifiStatus);
	}
    }
    

    private void requestAccountCredits()
    {   
	// Guthaben aufladen: *104*AUFLADECODE#
	
	//String encoded = "tel:" + Uri.encode("*#06#");
	String encoded = "tel:*100" + Uri.encode("#");
	startActivityForResult(new Intent("android.intent.action.CALL", Uri.parse(encoded)), 1);
	/*
	Thread t = new Thread()
	{
	    @Override
	    public void run() 
	    {
		USSD ussd = new USSD(MainActivity.this);
	    };
	};
	
	t.start();
	*/
    }

    private final static String TAG            = MainActivity.class.getSimpleName();
    
    private final static int    MP3_TICK       = R.raw.tick;
    private final static int    MP3_CONNECTION = R.raw.connection;
    private final static int    MP3_ENABLED    = R.raw.enabled;
    private final static int    MP3_DISABLED   = R.raw.disabled;
    
    private final static int    MSG_TRAFFIC    = 4711;
    private final static int    MSG_CLIENTS    = 4712;
    
    private boolean mSavedWifiStatus;
    private boolean mWithSound = true;

    private RelativeLayout mLayout;
    
    private Button mButtonEnableMifi;
    private Button mButtonDisableMifi;
    private Button mButtonCheckCredits;
    
    private TextView mTextviewMobileData;
    private TextView mTextviewAccessPoint;
    private TextView mTextviewTraffic;
    private TextView mTextviewClients;
    
    private StatsThread mStatsThread;
    
    private Vibrator     mVibrator;
    private AudioManager mAudioManager;

}