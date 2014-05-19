/*
 * Copyright (c) 2011-2012, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Locale;

import org.apache.http.HttpHost;

import android.accounts.AccountAuthenticatorActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Proxy;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.ClientManager.ProxyOptions;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.OAuthWebviewHelperEvents;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Login Activity: takes care of authenticating the user.
 * Authorization happens inside a web view. Once we get our authorization code,
 * we swap it for an access and refresh token a create an account through the
 * account manager to store them.
 *
 * The bulk of the work for this is actually managed by OAuthWebviewHelper class.
 */
public class LoginActivity extends AccountAuthenticatorActivity implements OAuthWebviewHelperEvents {

	// Request code when calling server picker activity
    public static final int PICK_SERVER_REQUEST_CODE = 10;

    private SalesforceR salesforceR;
	private boolean wasBackgrounded;
	private OAuthWebviewHelper webviewHelper;
	private View loadSpinner;
	private View loadSeparator;
	
    /**************************************************************************************************
     *
     * Activity lifecycle
     *
     **************************************************************************************************/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Object which allows reference to resources living outside the SDK
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();

		// Getting login options from intent's extras
		LoginOptions loginOptions = LoginOptions.fromBundle(getIntent().getExtras());
		
		// Getting login options from intent's extras
		ProxyOptions proxyOptions = SalesforceSDKManager.getProxyOptions();
		
		
		
		// We'll show progress in the window title bar
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		
		
		// Setup content view
		setContentView(salesforceR.layoutLogin());
        loadSpinner = findViewById(salesforceR.idLoadSpinner());
        loadSeparator = findViewById(salesforceR.idLoadSeparator());

		// Setup the WebView.
		WebView webView = (WebView) findViewById(salesforceR.idLoginWebView());
		webView.getSettings().setSavePassword(false);
		//setProxy(webView, proxyOptions.proxyHost, proxyOptions.proxyPort, proxyOptions.proxyUser, proxyOptions.proxyPass);
		EventsObservable.get().notifyEvent(EventType.AuthWebViewCreateComplete, webView);
		
		//TFK ADDING PROXY OPTIONS TO WEBHELPER
		webviewHelper = getOAuthWebviewHelper(this, loginOptions, webView, savedInstanceState, proxyOptions);
		
		// Let observers know
		EventsObservable.get().notifyEvent(EventType.LoginActivityCreateComplete, this);        

		// Load login page
		webviewHelper.loadLoginPage();
	
	}
	
	/**
	 * TFK
	 */
	
	public static boolean setProxy(WebView webview, String proxyHost, String proxyPort, String proxyUser, String proxyPass) {
		String applicationClassName = "android.app.Application";
		int port;
		try{ port = Integer.valueOf(proxyPort); } catch(Exception ex) {port = 0;}
		
		// 3.2 (HC) or lower
	    if (Build.VERSION.SDK_INT <= 13) {
	        return setProxyUpToHC(webview, proxyHost, port);
	    }
	    // ICS: 4.0
	    else if (Build.VERSION.SDK_INT <= 15) {
	        return setProxyICS(webview, proxyHost, port);
	    }
	    // 4.1-4.3 (JB)
	    else if (Build.VERSION.SDK_INT <= 18) {
	        return setProxyJB(webview, proxyHost, port);
	    }
	    // 4.4 (KK)
	    else {
	        return setProxyKK(webview, proxyHost, port, proxyUser, proxyPass,applicationClassName);
	    }
	}

	/**
	 * Set Proxy for Android 3.2 and below.
	 */
	@SuppressWarnings("all")
	private static boolean setProxyUpToHC(WebView webview, String host, int port) {
	    Log.d("Toufiik**************************", "Setting proxy with <= 3.2 API.");

	    HttpHost proxyServer = new HttpHost(host, port);
	    // Getting network
	    Class networkClass = null;
	    Object network = null;
	    try {
	        networkClass = Class.forName("android.webkit.Network");
	        if (networkClass == null) {
	            Log.e("Toufiik**************************", "failed to get class for android.webkit.Network");
	            return false;
	        }
	        Method getInstanceMethod = networkClass.getMethod("getInstance", Context.class);
	        if (getInstanceMethod == null) {
	            Log.e("Toufiik**************************", "failed to get getInstance method");
	        }
	        network = getInstanceMethod.invoke(networkClass, new Object[]{webview.getContext()});
	    } catch (Exception ex) {
	        Log.e("Toufiik**************************", "error getting network: " + ex);
	        return false;
	    }
	    if (network == null) {
	        Log.e("Toufiik**************************", "error getting network: network is null");
	        return false;
	    }
	    Object requestQueue = null;
	    try {
	        Field requestQueueField = networkClass
	                .getDeclaredField("mRequestQueue");
	        requestQueue = getFieldValueSafely(requestQueueField, network);
	    } catch (Exception ex) {
	        Log.e("Toufiik**************************", "error getting field value");
	        return false;
	    }
	    if (requestQueue == null) {
	        Log.e("Toufiik**************************", "Request queue is null");
	        return false;
	    }
	    Field proxyHostField = null;
	    try {
	        Class requestQueueClass = Class.forName("android.net.http.RequestQueue");
	        proxyHostField = requestQueueClass
	                .getDeclaredField("mProxyHost");
	    } catch (Exception ex) {
	        Log.e("Toufiik**************************", "error getting proxy host field");
	        return false;
	    }

	    boolean temp = proxyHostField.isAccessible();
	    try {
	        proxyHostField.setAccessible(true);
	        proxyHostField.set(requestQueue, proxyServer);
	    } catch (Exception ex) {
	        Log.e("Toufiik**************************", "error setting proxy host");
	    } finally {
	        proxyHostField.setAccessible(temp);
	    }

	    Log.d("Toufiik**************************", "Setting proxy with <= 3.2 API successful!");
	    return true;
	}

	@SuppressWarnings("all")
	private static boolean setProxyICS(WebView webview, String host, int port) {
	    try
	    {
	        Log.d("Toufiik**************************", "Setting proxy with 4.0 API.");

	        Class jwcjb = Class.forName("android.webkit.JWebCoreJavaBridge");
	        Class params[] = new Class[1];
	        params[0] = Class.forName("android.net.ProxyProperties");
	        Method updateProxyInstance = jwcjb.getDeclaredMethod("updateProxy", params);

	        Class wv = Class.forName("android.webkit.WebView");
	        Field mWebViewCoreField = wv.getDeclaredField("mWebViewCore");
	        Object mWebViewCoreFieldInstance = getFieldValueSafely(mWebViewCoreField, webview);

	        Class wvc = Class.forName("android.webkit.WebViewCore");
	        Field mBrowserFrameField = wvc.getDeclaredField("mBrowserFrame");
	        Object mBrowserFrame = getFieldValueSafely(mBrowserFrameField, mWebViewCoreFieldInstance);

	        Class bf = Class.forName("android.webkit.BrowserFrame");
	        Field sJavaBridgeField = bf.getDeclaredField("sJavaBridge");
	        Object sJavaBridge = getFieldValueSafely(sJavaBridgeField, mBrowserFrame);

	        Class ppclass = Class.forName("android.net.ProxyProperties");
	        Class pparams[] = new Class[3];
	        pparams[0] = String.class;
	        pparams[1] = int.class;
	        pparams[2] = String.class;
	        Constructor ppcont = ppclass.getConstructor(pparams);

	        updateProxyInstance.invoke(sJavaBridge, ppcont.newInstance(host, port, null));

	        Log.d("Toufiik**************************", "Setting proxy with 4.0 API successful!");
	        return true;
	    }
	    catch (Exception ex)
	    {
	        Log.e("Toufiik**************************", "failed to set HTTP proxy: " + ex);
	        return false;
	    }
	}

	/**
	 * Set Proxy for Android 4.1 - 4.3.
	 */
	@SuppressWarnings("all")
	private static boolean setProxyJB(WebView webview, String host, int port) {
	    Log.d("Toufiik**************************", "Setting proxy with 4.1 - 4.3 API.");

	    try {
	        Class wvcClass = Class.forName("android.webkit.WebViewClassic");
	        Class wvParams[] = new Class[1];
	        wvParams[0] = Class.forName("android.webkit.WebView");
	        Method fromWebView = wvcClass.getDeclaredMethod("fromWebView", wvParams);
	        Object webViewClassic = fromWebView.invoke(null, webview);

	        Class wv = Class.forName("android.webkit.WebViewClassic");
	        Field mWebViewCoreField = wv.getDeclaredField("mWebViewCore");
	        Object mWebViewCoreFieldInstance = getFieldValueSafely(mWebViewCoreField, webViewClassic);

	        Class wvc = Class.forName("android.webkit.WebViewCore");
	        Field mBrowserFrameField = wvc.getDeclaredField("mBrowserFrame");
	        Object mBrowserFrame = getFieldValueSafely(mBrowserFrameField, mWebViewCoreFieldInstance);

	        Class bf = Class.forName("android.webkit.BrowserFrame");
	        Field sJavaBridgeField = bf.getDeclaredField("sJavaBridge");
	        Object sJavaBridge = getFieldValueSafely(sJavaBridgeField, mBrowserFrame);

	        Class ppclass = Class.forName("android.net.ProxyProperties");
	        Class pparams[] = new Class[3];
	        pparams[0] = String.class;
	        pparams[1] = int.class;
	        pparams[2] = String.class;
	        Constructor ppcont = ppclass.getConstructor(pparams);

	        Class jwcjb = Class.forName("android.webkit.JWebCoreJavaBridge");
	        Class params[] = new Class[1];
	        params[0] = Class.forName("android.net.ProxyProperties");
	        Method updateProxyInstance = jwcjb.getDeclaredMethod("updateProxy", params);
	        updateProxyInstance.invoke(sJavaBridge, ppcont.newInstance("192.168.153.1", 808, null));
	    } catch (Exception ex) {
	        Log.e("Toufiik**************************","Setting proxy with >= 4.1 API failed with error: " + ex.getMessage());
	        return false;
	    }

	    Log.d("Toufiik**************************", "Setting proxy with 4.1 - 4.3 API successful!");
	    return true;
	}

	// from https://stackoverflow.com/questions/19979578/android-webview-set-proxy-programatically-kitkat
	@SuppressLint("NewApi")
	@SuppressWarnings("all")
	private static boolean setProxyKK(WebView webView, String proxyHost, int proxyPort, String proxyUser, String proxyPass,String applicationClassName) {
	    Log.d("Toufiik**************************", "Setting proxy with >= 4.4 API.");
	    
	   
	    Context appContext = webView.getContext().getApplicationContext();
	    System.setProperty("http.proxyHost", proxyHost);
	    System.setProperty("http.proxyPort", proxyPort + "");
	    System.setProperty("https.proxyHost", proxyHost);
	    System.setProperty("https.proxyPort", proxyPort + "");
	    
	    // TFK : 
	    
	    final String authUser = proxyUser;
	    final String authPassword = proxyPass;
	    Authenticator.setDefault(
	       new Authenticator() {
	          public PasswordAuthentication getPasswordAuthentication() {
	             return new PasswordAuthentication(
	                   authUser, authPassword.toCharArray());
	          }
	       }
	    );

	    System.setProperty("http.proxyUser", authUser);
	    System.setProperty("http.proxyPassword", authPassword);

	    try {
	        Class applictionCls = Class.forName(applicationClassName);
	        Field loadedApkField = applictionCls.getField("mLoadedApk");
	        loadedApkField.setAccessible(true);
	        Object loadedApk = loadedApkField.get(appContext);
	        Class loadedApkCls = Class.forName("android.app.LoadedApk");
	        Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
	        receiversField.setAccessible(true);
	        ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
	        for (Object receiverMap : receivers.values()) {
	            for (Object rec : ((ArrayMap) receiverMap).keySet()) {
	                Class clazz = rec.getClass();
	                if (clazz.getName().contains("ProxyChangeListener")) {
	                    Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
	                    Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);

	                    /*********** optional, may be need in future *************/
	                    final String CLASS_NAME = "android.net.ProxyProperties";
	                    Class cls = Class.forName(CLASS_NAME);
	                    Constructor constructor = cls.getConstructor(String.class, Integer.TYPE, String.class);
	                    constructor.setAccessible(true);
	                    Object proxyProperties = constructor.newInstance(proxyHost, proxyPort, null);
	                    intent.putExtra("proxy", (Parcelable) proxyProperties);
	                    /*********** optional, may be need in future *************/

	                    onReceiveMethod.invoke(rec, appContext, intent);
	                }
	            }
	        }

	        Log.d("Toufiik**************************", "Setting proxy with >= 4.4 API successful!");
	        return true;
	    } catch (ClassNotFoundException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (NoSuchFieldException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (IllegalAccessException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (IllegalArgumentException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (NoSuchMethodException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (InvocationTargetException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    } catch (InstantiationException e) {
	        StringWriter sw = new StringWriter();
	        e.printStackTrace(new PrintWriter(sw));
	        String exceptionAsString = sw.toString();
	        Log.v("Toufiik**************************", e.getMessage());
	        Log.v("Toufiik**************************", exceptionAsString);
	    }
	    return false;
	}

	private static Object getFieldValueSafely(Field field, Object classInstance) throws IllegalArgumentException, IllegalAccessException {
	    boolean oldAccessibleValue = field.isAccessible();
	    field.setAccessible(true);
	    Object result = field.get(classInstance);
	    field.setAccessible(oldAccessibleValue);
	    return result;
	}
	
	
	protected OAuthWebviewHelper getOAuthWebviewHelper(OAuthWebviewHelperEvents callback, LoginOptions loginOptions, WebView webView, Bundle savedInstanceState, ProxyOptions proxyOptions) {
		return new OAuthWebviewHelper(callback, loginOptions, webView, savedInstanceState, proxyOptions);
	}



	@Override
	protected void onResume() {
		super.onResume();
	    if (wasBackgrounded) {
			webviewHelper.clearView();
			webviewHelper.loadLoginPage();		
			wasBackgrounded = false;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		webviewHelper.saveState(bundle);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			wasBackgrounded = true;
			moveTaskToBack(true);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

    /**************************************************************************************************
     *
     * Actions (Changer server / Clear cookies etc) are available through a menu
     *
     **************************************************************************************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(salesforceR.menuLogin(), menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        /*
         * The only way to customize the title of a menu item is to do
         * it through code. While this is a dirty hack, there appears to
         * be no other way to ellipsize the title of a menu item.
         * The overflow occurs only when the locale is German, and hence,
         * the text is ellipsized just for the German locale.
         */
        final Locale locale = getResources().getConfiguration().locale;
        if (locale.equals(Locale.GERMANY) || locale.equals(Locale.GERMAN)) {
                for (int i = 0; i < menu.size(); i++) {
                final MenuItem item = menu.getItem(i);
                final String fullTitle = item.getTitle().toString();
                if (fullTitle != null && fullTitle.length() > 8) {
                    item.setTitle(fullTitle.substring(0, 8) + "...");
                }
            }
        }
        return true;
    }

    /**
     * handle main menu clicks
     */
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int itemId = item.getItemId();
		if (itemId == salesforceR.idItemClearCookies()) {
        	onClearCookiesClick(null);
        	return true;
        }
        else if (itemId == salesforceR.idItemPickServer()) {
        	onPickServerClick(null);
        	return true;
        }
        else if (itemId == salesforceR.idItemReload()) {
        	onReloadClick(null);
        	return true;
        }
        else {
            return super.onMenuItemSelected(featureId, item);
        }
    }

    /**************************************************************************************************
     *
     * Callbacks from the OAuthWebviewHelper
     *
     **************************************************************************************************/

	@Override
	public void loadingLoginPage(String loginUrl) {
        TextView serverName = (TextView) findViewById(salesforceR.idServerName());
        if (serverName != null) {
                serverName.setText(loginUrl);
        }
	}

	@Override
	public void onLoadingProgress(int totalProgress) {
		onIndeterminateProgress(false);
		setProgress(totalProgress);
		if (loadSpinner != null) {
			loadSpinner.setVisibility(totalProgress < Window.PROGRESS_END ? View.VISIBLE : View.GONE);
		}
		if (loadSeparator != null) {
			loadSeparator.setVisibility(totalProgress < Window.PROGRESS_END ? View.VISIBLE : View.GONE);
		}
	}

	@Override
	public void onIndeterminateProgress(boolean show) {
		setProgressBarIndeterminateVisibility(show);
		setProgressBarIndeterminate(show);
	}

	@Override
	public void onAccountAuthenticatorResult(Bundle authResult) {
		setAccountAuthenticatorResult(authResult);
	}

    /**************************************************************************************************
     *
     * Buttons click handlers
     *
     **************************************************************************************************/

	/**
	 * Called when "Clear cookies" button is clicked.
	 * Clear cookies and reload login page.
	 * @param v
	 */
	public void onClearCookiesClick(View v) {
		webviewHelper.clearCookies();
		webviewHelper.loadLoginPage();
		
	}

	/**
	 * Called when "Reload" button is clicked.
	 * Reloads login page.
	 * @param v
	 */
	public void onReloadClick(View v) {
		webviewHelper.loadLoginPage();
	}

	/**
	 * Called when "Pick server" button is clicked.
	 * Start ServerPickerActivity
	 * @param v
	 */
	public void onPickServerClick(View v) {
		Intent i = new Intent(this, ServerPickerActivity.class);
	    startActivityForResult(i, PICK_SERVER_REQUEST_CODE);
	}

	/**
	 * Called when ServerPickerActivity completes.
	 * Reload login page.
	 */
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_SERVER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			webviewHelper.loadLoginPage();
			
		}
		else if (requestCode == PasscodeManager.PASSCODE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			webviewHelper.onNewPasscode();
			
		}
		else {
	        super.onActivityResult(requestCode, resultCode, data);
	    	
	    }
	}
}
