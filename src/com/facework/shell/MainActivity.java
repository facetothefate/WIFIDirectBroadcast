package com.facework.shell;





import com.example.wifidirectbroadcast.R;
import com.facework.configuration.ServerConf;
import com.facework.core.wifidirect.DeviceDetailFragment;
import com.facework.core.wifidirect.DeviceListFragment;

import net.facework.core.streaming.Session;
import net.facework.core.streaming.SessionManager;
import net.facework.core.streaming.misc.ECRTP2RTPTunnel;
import net.facework.core.streaming.misc.RtspServer;
import net.facework.core.streaming.misc.SocketTunnel;
import net.facework.core.streaming.transportPacketizer.ECRtpSocket;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings; 
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.facework.core.wifidirect.DeviceListFragment.DeviceActionListener;
import com.facework.core.wifidirect.WiFiDirectBroadcastReceiver;

public class MainActivity extends Activity implements ChannelListener, DeviceActionListener  {

	public static final String TAG = "wifidirectBroadcast";
	//////////////////////////////////
	// Server service
	//////////////////////////////////
	
	// RTSP server service;
	private RtspServer 		mRtspServer;
	private RtspServer.CallbackListener mRtspCallbackListener = new RtspServer.CallbackListener() {

		@Override
		public void onError(RtspServer server, Exception e, int error) {
			// We alert the user that the port is already used by another app.
			if (error == RtspServer.ERROR_BIND_FAILED) {
				new AlertDialog.Builder(MainActivity.this)
				.setTitle(R.string.port_used)
				.setMessage(getString(R.string.bind_failed, "RTSP"))
				.show();
			}
		}

	};
	private ServiceConnection mRtspServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mRtspServer = ((RtspServer.LocalBinder)service).getService();
			mRtspServer.addCallbackListener(mRtspCallbackListener);
			Log.i("RTSP server","try to start RTSP server");
			mRtspServer.start();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {}

	};
	
	// ECRTP2RTP Tunnel service;
	private ECRTP2RTPTunnel mE2RTunnel;
	private ECRTP2RTPTunnel.CallbackListener mE2RTunnelCallbackListener = new ECRTP2RTPTunnel.CallbackListener() {

		@Override
		public void onError(ECRTP2RTPTunnel server, Exception e, int error) {
			// We alert the user that the port is already used by another app.
			if (error == RtspServer.ERROR_BIND_FAILED) {
				new AlertDialog.Builder(MainActivity.this)
				.setTitle(R.string.port_used)
				.setMessage(getString(R.string.bind_failed, "ECRTP2RTP Tunnel"))
				.show();
			}
		}

	};
	private ServiceConnection mE2RTunnelConnection = new ServiceConnection(){
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mE2RTunnel = ((ECRTP2RTPTunnel.LocalBinder)service).getService();
			mE2RTunnel.addCallbackListener(mE2RTunnelCallbackListener);
			Log.i("ECRTP2RTP Tunnel","try to start ECRTP2RTP Tunnel");
			mE2RTunnel.start();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {}
	};
	
	private SurfaceView 	mSurfaceView;
	private SurfaceHolder 	mSurfaceHolder;
	
	//////////////////////////////////
	// WiFi Direct
	//////////////////////////////////
	private WifiP2pManager manager;
	private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    
    //////////////////////////////////
    // Player
    //////////////////////////////////
    public static VideoView player=null;
    public static Dialog loadingDialog;
    private TextView 	IP=null;
    
    //////////////////////////////////
    //  Server Settings' UI
    //////////////////////////////////
    private SeekBar 	seekBar =null ;
    private Switch 		tunnelSw=null;
    //private Switch 		RTPSw=null;
    private Switch 		RTPSwV2=null;
    private Context 	mContext =this;
    private RadioGroup 	lossClientRadioGroup=null;
    private RadioButton lossClientItem1=null;
    private RadioButton lossClientItem2=null;
    private RadioButton lossClientItem3=null;
    private RadioButton lossClientItem4=null;
    private RadioButton lossClientItem5=null;
    private RadioButton lossClientItem6=null;
    private EditText 	blockSize=null;
    private EditText 	addtionalSend=null;
    
    
    
    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }
	
	public void log(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        player=(VideoView) this.findViewById(R.id.preview);
        player.setMediaController(new MediaController(this));
        player.setOnPreparedListener(new OnPreparedListener() {
        		//@Override
        		@Override
				public void onPrepared(MediaPlayer mp) {
        			player.setBackgroundColor(Color.argb(0, 0, 255, 0));
        			loadingDialog.dismiss();
        		}
        	});
        
        
        //player.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		//START RTSP server
		
        this.startService(new Intent(this.getApplicationContext() ,RtspServer.class));
        //this.startService(new Intent(this.getApplicationContext() ,SocketTunnel.class));
        this.startService(new Intent(this.getApplicationContext() ,ECRTP2RTPTunnel.class));
		mSurfaceView = (SurfaceView)findViewById(R.id.preview);
		mSurfaceHolder = mSurfaceView.getHolder();
		// We still need this line for backward compatibility reasons with android 2
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		SessionManager.getManager().setSurfaceHolder(mSurfaceHolder, false);
		//Log.i("RTSP server","try to start RTSP server service");
		// add necessary intent values to be matched.

		
		// used for input IP address
		IP=(TextView) this.findViewById(R.id.IP_address);
		this.findViewById(R.id.IP_play).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                    	loadingDialog=ProgressDialog.show( mContext, "loading...", "please wait");
                    	Log.i("RTSP Player","opening "+Uri.parse(IP.getText().toString()));
                    	MainActivity.player.setVideoURI(Uri.parse(IP.getText().toString()));
                    	MainActivity.player.requestFocus();
                    	MainActivity.player.setBackgroundColor(Color.argb(0, 0, 255, 0));
                    	MainActivity.player.start();
                    }
                });
	}


	/** register the BroadcastReceiver with the i6rdxvntent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
        
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
	
	@Override
	public void onStart() {
		super.onStart();
		bindService(new Intent(this,RtspServer.class), mRtspServiceConnection, Context.BIND_AUTO_CREATE);
		//bindService(new Intent(this,SocketTunnel.class), mSocketTunnelConnection, Context.BIND_AUTO_CREATE);
		bindService(new Intent(this,ECRTP2RTPTunnel.class), mE2RTunnelConnection, Context.BIND_AUTO_CREATE);
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mRtspServiceConnection);
		//unbindService(mSocketTunnelConnection);
		unbindService(mE2RTunnelConnection);
	}
	
	
		
	
	//wifi-direct 
	
	/**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }
    public boolean startToseachPeer(){
    	
    	 if (!isWifiP2pEnabled) {
             Toast.makeText(MainActivity.this, R.string.p2p_off_warning,
                     Toast.LENGTH_SHORT).show();
             return true;
         }
         final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                 .findFragmentById(R.id.frag_list);
         fragment.onInitiateDiscovery();
         manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

             @Override
             public void onSuccess() {
                 Toast.makeText(MainActivity.this, "Discovery Initiated",
                         Toast.LENGTH_SHORT).show();
             }

             @Override
             public void onFailure(int reasonCode) {
                 Toast.makeText(MainActivity.this, "Discovery Failed : " + reasonCode,
                         Toast.LENGTH_SHORT).show();
             }
         });
         return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.
                	
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } else {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_direct_discover:
            	startToseachPeer();
            	return true;
            case R.id.atn_exit:
            	System.exit(0);
            	return true;
            case R.id.atn_options:
            	LayoutInflater inflater = getLayoutInflater();
            	   View layout = inflater.inflate(R.layout.setting,
            	     (ViewGroup) findViewById(R.id.setting));
            	   
                   
            	   new AlertDialog.Builder(this).setTitle("Server settings").setView(layout)
            	     .setNegativeButton("Close", null).show();
            	   
					////////////////////////////////////////////////////////////////////////
					// loss simulation settings
					////////////////////////////////////////////////////////////////////////
            	  
           			
           			lossClientRadioGroup=(RadioGroup)layout.findViewById(R.id.setting_loss_client_radioGroup);
           		    lossClientItem1=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_0);
           		    lossClientItem2=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_1);
           		    lossClientItem3=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_2);
           		    lossClientItem4=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_3);
           		    lossClientItem5=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_4);
           		    lossClientItem6=(RadioButton)layout.findViewById(R.id.setting_loss_client_item_5);
           		    switch(ServerConf.CLIENT_LOSS){
           		    	case 0: 	lossClientRadioGroup.check(lossClientItem1.getId());break;
           		    	case 10: 	lossClientRadioGroup.check(lossClientItem2.getId());break;
           		    	case 20: 	lossClientRadioGroup.check(lossClientItem3.getId());break;
           		    	case 30: 	lossClientRadioGroup.check(lossClientItem4.getId());break;
           		    	case 40: 	lossClientRadioGroup.check(lossClientItem5.getId());break;
           		    	case 80: 	lossClientRadioGroup.check(lossClientItem6.getId());break;
           		    }
	           		lossClientRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {  
	                     @Override  
	                     public void onCheckedChanged(RadioGroup group, int checkedId) {  
	                         	if(checkedId==lossClientItem1.getId()){ServerConf.CLIENT_LOSS=0;}
	                         	if(checkedId==lossClientItem2.getId()){ServerConf.CLIENT_LOSS=10;}
	                         	if(checkedId==lossClientItem3.getId()){ServerConf.CLIENT_LOSS=20;}
	                         	if(checkedId==lossClientItem4.getId()){ServerConf.CLIENT_LOSS=30;}
	                         	if(checkedId==lossClientItem5.getId()){ServerConf.CLIENT_LOSS=40;}
	                         	if(checkedId==lossClientItem6.getId()){ServerConf.CLIENT_LOSS=80;}
	                     }  
	           		});   
           			////////////////////////////////////////////////////////////////////////
           			// Protocol settings
           			////////////////////////////////////////////////////////////////////////
	           		
	           		addtionalSend=(EditText)layout.findViewById(R.id.setting_addttional_send_value);
	           		addtionalSend.setText(Integer.toString(ServerConf.ADDTIONAL_PACKETS_NUMBER));
	           		blockSize=(EditText)layout.findViewById(R.id.setting_block_size_Value);
	           		blockSize.setText(Integer.toString(ServerConf.BLOCK_SIZE));
	           		
	           		addtionalSend.addTextChangedListener(new TextWatcher() {           
	           		    @Override
	           		    public void onTextChanged(CharSequence s, int start, int before, int count) {
	           		    	ServerConf.ADDTIONAL_PACKETS_NUMBER=Integer.parseInt(addtionalSend.getText().toString());
	           		    }
	           		     
	           		    @Override
	           		    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	           		    }
						@Override
						public void afterTextChanged(Editable arg0) {
							// TODO Auto-generated method stub
						}
	           		});
	           		
	           		blockSize.addTextChangedListener(new TextWatcher() {           
	           		    @Override
	           		    public void onTextChanged(CharSequence s, int start, int before, int count) {
	           		    	ServerConf.BLOCK_SIZE=Integer.parseInt(blockSize.getText().toString());
	           		    }
	           		     
	           		    @Override
	           		    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	           		    }
						@Override
						public void afterTextChanged(Editable arg0) {
							// TODO Auto-generated method stub
							
						}
	           		});
	           		
           			tunnelSw=(Switch)layout.findViewById(R.id.setting_tunnel_switch);
           			tunnelSw.setChecked(ServerConf.TUNNEL);

           			RTPSwV2=(Switch)layout.findViewById(R.id.setting_rtp_switch1);
           			if(ServerConf.TRANS==ServerConf.EC_RTP_V2)
           				RTPSwV2.setChecked(true);
           			tunnelSw.setOnCheckedChangeListener(new OnCheckedChangeListener() {  
                        
           	            @Override  
           	            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {  
           	                if(isChecked) {  
           	                	ServerConf.TUNNEL=ServerConf.ON; 
           	                	ServerConf.TRANS=ServerConf.EC_RTP_V2;
           	                	RTPSwV2.setChecked(true);
           	                	
           	                } else {    
           	                	ServerConf.TUNNEL=ServerConf.OFF;
           	                }  
           	                  
           	            }

           	        }); 

           			RTPSwV2.setOnCheckedChangeListener(new OnCheckedChangeListener() {  
                        
           	            @Override  
           	            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {  
           	                if(isChecked) {  
           	                	ServerConf.TRANS=ServerConf.EC_RTP_V2;
           	                } else {  
           	                	ServerConf.TRANS=ServerConf.RTP;
           	                }  
           	                  
           	            }

           	        }); 
            	   
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(MainActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }

}
