package com.njackson;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import com.google.android.gms.location.LocationListener;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.getpebble.android.kit.PebbleKit;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

import fr.jayps.android.AdvancedLocation;

/**
 * Created with IntelliJ IDEA.
 * User: njackson
 * Date: 23/05/2013
 * Time: 13:30
 * To change this template use File | Settings | File Templates.
 */
public class GPSService extends Service implements GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {

    private int _updates;
    private LocationClient _locationClient;
    private double _speed;
    private double _averageSpeed;
    private double _distance;

    private double _prevspeed = -1;
    private double _prevaverageSpeed = -1;
    private double _prevdistance = -1;

    private AdvancedLocation _myLocation;

    private static double _speedConversion = 0.0;
    private static double _distanceConversion = 0.0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        
        _myLocation = new AdvancedLocation(getApplicationContext());
        
        return START_STICKY;
    }

    @Override
    public void onDestroy (){
        Log.d("MainActivity","Stopped GPS Service");

        // save the state
        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME,0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat("GPS_SPEED", (float) _speed);
        editor.putFloat("GPS_DISTANCE",(float)_distance);
        editor.putFloat("GPS_AVGSPEED",(float)_averageSpeed);
        editor.putInt("GPS_UPDATES", _updates);
        editor.commit();

        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCH_UUID);
        _locationClient.removeLocationUpdates(this);
        _locationClient.disconnect();
    }

    private void handleCommand(Intent intent) {
        Log.d("MainActivity","Started GPS Service");

        // set the units to be used
        int units = intent.getIntExtra("UNITS",1);
        setConversionUnits(units);

        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME,0);
        _speed = (double)settings.getFloat("GPS_SPEED",0);
        _distance = (double)settings.getFloat("GPS_DISTANCE",0);
        _averageSpeed = (double)settings.getFloat("GPS_AVGSPEED",0);

        try {
        _updates = settings.getInt("GPS_UPDATES",0);
        }catch (ClassCastException e) {
            _updates = 0;
        }
        _locationClient = new LocationClient(getApplicationContext(),this,this);
        _locationClient.connect();

        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCH_UUID);
    }

    public static void setConversionUnits(int units) {
        if(units == Constants.IMPERIAL) {
            _speedConversion = Constants.MS_TO_MPH;
            _distanceConversion = Constants.M_TO_MILES;
        } else {
            _speedConversion = Constants.MS_TO_KPH;
            _distanceConversion = Constants.M_TO_KM;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d("MainActivity","GPS CONNECTED");
        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setInterval(500);
        //request.setFastestInterval(500);
        _locationClient.requestLocationUpdates(request, this);
    }

    @Override
    public void onDisconnected() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onLocationChanged(Location location) {
        _myLocation.onLocationChanged(location);
        
        Log.d("MainActivity", "Got Speed: " + _myLocation.getSpeed());

        _speed = _myLocation.getSpeed() * _speedConversion;

        if(_speed < 1) {
            _speed = 0;
        } else {
            _updates++;
        }

        _averageSpeed = _myLocation.getAverageSpeed() * _speedConversion;
        _distance = _myLocation.getDistance() * _distanceConversion;
        
        // available:
        //_myLocation.getElapsedTime() // in ms
        //_myLocation.getAltitude() // in m
        //_myLocation.getGoodAltitude() // in m

        updatePebble();

    }

    private void updatePebble() {

        if(_speed != _prevspeed || _averageSpeed != _prevaverageSpeed || _distance != _prevdistance) {
            /*
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(MainActivity.GPSServiceReceiver.ACTION_RESP);
            broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
            broadcastIntent.putExtra("SPEED", _speed);
            broadcastIntent.putExtra("DISTANCE", _distance);
            broadcastIntent.putExtra("AVGSPEED", _averageSpeed);
            sendBroadcast(broadcastIntent);
            */
            _prevaverageSpeed = _averageSpeed;
            _prevdistance = _distance;
            _prevspeed = _speed;

        }
    }
}