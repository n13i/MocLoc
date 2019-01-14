package net.m2hq.mocloc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MockLocationService extends Service
{
    private LocationManager mLocationManager;
    private Location mLocation;

    private NotificationCompat.Builder mBuilder;

    private static final String CHANNEL_ID = "net.m2hq.mocloc";
    private static final int NOTIFICATION_ID = 1;

    private DatagramSocket mDatagramSocket;

    private UDPReceiveThread mUDPReceiveThread;

    private boolean mGpsFixed = false;
    private int mSatellitesCount = 0;
    private int mSatellitesUsed = 0;

    @Override
    public void onCreate()
    {
        super.onCreate();

        createNotificationChannel();

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_gps_off);

        mLocation = new Location(LocationManager.GPS_PROVIDER);

        mLocationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        mLocationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE);
        mLocationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        mLocationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null, SystemClock.elapsedRealtime());

        mUDPReceiveThread = new UDPReceiveThread();
        mUDPReceiveThread.start();

        TimerTask task = new TimerTask() {
            @Override
            public void run()
            {
                showNotification();
            }
        };
        Timer t = new Timer();
        t.scheduleAtFixedRate(task, 0, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);

        mLocationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    private void createNotificationChannel()
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification()
    {
        if (!mUDPReceiveThread.isConnected())
        {
            mBuilder.setContentText("Disconnected");
            mBuilder.setSmallIcon(R.drawable.ic_gps_off);
        }
        else
        {
            SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
            String date = df.format(mLocation.getTime());

            if (mGpsFixed)
            {
                mBuilder.setContentText(String.format(Locale.US, "%s | FIXED | Sats %d/%d", date, mSatellitesUsed, mSatellitesCount));
                mBuilder.setSmallIcon(R.drawable.ic_gps_fixed);
            } else
            {
                mBuilder.setContentText(String.format(Locale.US, "%s | NO FIX", date));
                mBuilder.setSmallIcon(R.drawable.ic_gps_not_fixed);
            }
        }

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
    }

    class UDPReceiveThread extends Thread
    {
        private boolean mIsRunning;
        private long mLastReceived = 0;

        public UDPReceiveThread()
        {
            super();
            try
            {
                mDatagramSocket = new DatagramSocket(12947);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void start()
        {
            super.start();
        }

        @Override
        public void run()
        {
            byte buf[] = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            mIsRunning = true;
            while (mIsRunning)
            {
                try
                {
                    mDatagramSocket.receive(packet);

                    mLastReceived = System.currentTimeMillis();

                    String str = new String(buf, "UTF-8");
                    JSONObject json = new JSONObject(str);

                    //System.out.println(str);
                    updateLocation(json);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            mDatagramSocket.close();
            mDatagramSocket = null;
        }

        public boolean isConnected()
        {
            return System.currentTimeMillis() <= (mLastReceived + 5000);
        }
    }

    private void updateLocation(JSONObject json)
    {
        // http://catb.org/gpsd/gpsd_json.html
        try
        {
            if (json.has("class"))
            {
                String cls = json.getString("class");
                if (cls.equals("TPV"))
                {
                    if (json.getInt("mode") < 2)
                    {
                        mGpsFixed = false;
                    }
                    else
                    {
                        mGpsFixed = true;
                    }

                    if (json.has("lat") && json.has("lon"))
                    {
                        mLocation.setLatitude(json.getDouble("lat"));
                        mLocation.setLongitude(json.getDouble("lon"));
                    }
                    if (json.has("alt"))
                    {
                        mLocation.setAltitude(json.getDouble("alt"));
                    }

                    if (json.has("speed"))
                    {
                        mLocation.setSpeed((float)json.getDouble("speed"));
                    }

                    if (json.has("track"))
                    {
                        mLocation.setBearing((float) json.getDouble(("track")));
                    }

                    if (json.has("epx") && json.has("epy"))
                    {
                        float epx = (float)json.getDouble("epx");
                        float epy = (float)json.getDouble("epy");

                        mLocation.setAccuracy((epx > epy ? epx : epy));
                    }

                    if (json.has("epv") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    {
                        mLocation.setVerticalAccuracyMeters((float)json.getDouble("epv"));
                    }

                    if (json.has("eps") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    {
                        mLocation.setSpeedAccuracyMetersPerSecond((float) json.getDouble("eps"));
                    }

                    if (json.has("time"))
                    {
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        try
                        {
                            Date date = df.parse(json.getString("time"));

                            mLocation.setTime(date.getTime());
                            mLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                        }
                        catch(ParseException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    if (!mLocation.hasAccuracy())
                    {
                        mLocation.setAccuracy(500);
                    }

                    mLocationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mLocation);
                }

                if (cls.equals("SKY"))
                {
                    mSatellitesUsed = 0;
                    JSONArray sats = json.getJSONArray("satellites");
                    for (int i = 0; i < sats.length(); i++)
                    {
                        JSONObject obj = sats.getJSONObject(i);
                        if (obj.getBoolean("used"))
                        {
                            mSatellitesUsed++;
                        }
                    }
                    mSatellitesCount = sats.length();
                }

                showNotification();
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }
}
