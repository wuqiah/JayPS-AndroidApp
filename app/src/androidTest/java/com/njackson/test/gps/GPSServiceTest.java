package com.njackson.test.gps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.njackson.application.PebbleBikeModule;
import com.njackson.events.GPSService.ChangeState;
import com.njackson.events.GPSService.CurrentState;
import com.njackson.events.GPSService.NewLocation;
import com.njackson.events.GPSService.RefreshChange;
import com.njackson.events.GPSService.GPSResetEvent;
import com.njackson.gps.GPSService;
import com.njackson.test.application.TestApplication;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.mockito.ArgumentCaptor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by server on 28/04/2014.
 */
public class GPSServiceTest extends ServiceTestCase<GPSService>{

    @Inject Bus _bus = new Bus();
    @Inject LocationManager _mockLocationManager;
    @Inject SharedPreferences _mockPreferences;

    private GPSService _service;
    private Context _applicationContext;

    private SharedPreferences.Editor _mockEditor;

    private NewLocation _locationEventResults;

    private CurrentState _gpsDisabledEvent;

    @Module(
            includes = PebbleBikeModule.class,
            injects = GPSServiceTest.class,
            overrides = true
    )
    static class TestModule {
        @Provides
        @Singleton
        LocationManager provideLocationManager() {
            return mock(LocationManager.class);
        }

        @Provides
        @Singleton
        SharedPreferences provideSharedPreferences() {
            return mock(SharedPreferences.class);
        }
    }


    public GPSServiceTest(Class<GPSService> serviceClass) {
        super(serviceClass);
    }

    public GPSServiceTest() {
        super(GPSService.class);
    }

    //Bus Subscriptions
    @Subscribe
    public void onNewLocationEvent(NewLocation event) {
        _locationEventResults = event;
    }

    @Subscribe
    public void onGPSDisabledEvent(CurrentState event) {
        _gpsDisabledEvent = event;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getSystemContext().getCacheDir().getPath());

        TestApplication app = new TestApplication();
        app.setObjectGraph(ObjectGraph.create(TestModule.class));
        app.inject(this);
        _bus.register(this);

        setApplication(app);

        setupMocks();

        _locationEventResults = null; // reset the event results

    }

    private void startService() {
        Intent startIntent = new Intent(getSystemContext(), GPSService.class);
        startService(startIntent);
        _service = getService();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }


    private void setupMocks() {
        _mockEditor = mock(SharedPreferences.Editor.class, RETURNS_DEEP_STUBS);
        when(_mockPreferences.edit()).thenReturn(_mockEditor);
    }

    /*
    @SmallTest
    public void testSetsAdvancedLocationDebugLevel() {
        startService();
        assertNotNull(null);
    }
    */

    @SmallTest
    public void testBroadcastEventOnLocationDisabled() throws InterruptedException {
        when(_mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(false);

        startService();

        Thread.sleep(100);
        assertEquals(CurrentState.State.DISABLED, _gpsDisabledEvent.getState());
    }

    @SmallTest
    public void testBroadcastEventOnLocationChange() throws InterruptedException {
        when(_mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);

        startService();
        _bus.post(new ChangeState(ChangeState.Command.START));
        Thread.sleep(100);

        ArgumentCaptor<LocationListener> locationListenerCaptor = ArgumentCaptor.forClass(LocationListener.class);
        verify(_mockLocationManager).requestLocationUpdates(
                anyString(),
                anyLong(),
                anyFloat(),
                locationListenerCaptor.capture());

        Location location = new Location("location");
        LocationListener listenerArgument = locationListenerCaptor.getValue();
        listenerArgument.onLocationChanged(location);

        Thread.sleep(100);
        assertNotNull(_locationEventResults);
    }

    @SmallTest
    public void testHandlesGPSLocationReset() throws InterruptedException {
        startService();

        _bus.post(new ChangeState(ChangeState.Command.RESET));

        Thread.sleep(100);
        verify(_mockEditor, times(1)).putFloat("GPS_DISTANCE", 0.0f);
        verify(_mockEditor, times(1)).commit();
    }

    @SmallTest
    public void testHandlesGPSStartCommand() throws InterruptedException {
        startService();

        _bus.post(new ChangeState(ChangeState.Command.START));

        Thread.sleep(100);
        verify(_mockLocationManager,times(1)).requestLocationUpdates(
                anyString(),
                anyLong(),
                anyFloat(),
                any(LocationListener.class));
    }

    @SmallTest
    public void testHandlesRefreshInterval() throws InterruptedException {
        when(_mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);

        startService();
        _bus.post(new ChangeState(ChangeState.Command.START));
        Thread.sleep(100);

        ArgumentCaptor<LocationListener> locationListenerCaptor = ArgumentCaptor.forClass(LocationListener.class);
        verify(_mockLocationManager).requestLocationUpdates(
                anyString(),
                anyLong(),
                anyFloat(),
                locationListenerCaptor.capture());

        int refreshInterval = 200;
        _bus.post(new RefreshChange(refreshInterval));

        Thread.sleep(100);
        verify(_mockLocationManager, times(1)).removeUpdates((LocationListener) anyObject());
        verify(_mockLocationManager, times(1)).requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                refreshInterval,
                2,
                locationListenerCaptor.getValue()
        );
    }

    @SmallTest
    public void testSavesStateOnDestroy() {
        startService();
        _service.onDestroy();
        verify(_mockEditor, times(1)).commit();
    }
}