package net.m2hq.mocloc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

public class M5Hud
{
    private final static UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final static String DEVICE_NAME = "M5Hud";

    private BluetoothSocket mBtSocket;

    private long mTime;
    private byte mSatUsed;
    private byte mSatCount;
    private byte mSpeed;
    private short mBearing;

    private final static String TAG = M5Hud.class.getSimpleName();

    public M5Hud()
    {
    }

    // set UTC time in milliseconds since January 1, 1970
    public void setTime(long time)
    {
        mTime = time;
    }

    public void setSatUsed(int used)
    {
        mSatUsed = (byte)used;
    }

    public void setSatCount(int count)
    {
        mSatCount = (byte)count;
    }

    // set speed in m/s
    public void setSpeed(float speed)
    {
        // store in km/h
        mSpeed = (byte)(speed * 3600 / 1000);
    }

    public void setBearing(float bearing)
    {
        mBearing = (short)bearing;
    }

    public void connect()
    {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!btAdapter.isEnabled())
        {
            return;
        }

        Set<BluetoothDevice> devices = btAdapter.getBondedDevices();

        BluetoothDevice btDevice = null;
        for (BluetoothDevice dev : devices)
        {
            //Log.v(TAG, dev.getName());
            if (dev.getName().equals(DEVICE_NAME))
            {
                btDevice = dev;
                break;
            }
        }

        if (btDevice != null)
        {
            try
            {
                mBtSocket = btDevice.createRfcommSocketToServiceRecord(UUID_SPP);
                mBtSocket.connect();
            } catch (IOException e)
            {
                Log.w(TAG, Log.getStackTraceString(e));
            }
        }
    }

    public boolean isConnected()
    {
        if (mBtSocket == null)
        {
            return false;
        }

        return mBtSocket.isConnected();
    }

    public void send()
    {
        if (mBtSocket == null)
        {
            return;
        }

        Calendar cal = Calendar.getInstance();
        // set UTC time
        cal.setTimeInMillis(mTime);
        // get local time on device
        byte hour = (byte)cal.get(Calendar.HOUR_OF_DAY);
        byte minute = (byte)cal.get(Calendar.MINUTE);

        byte[] bytes = new byte[9];
        bytes[0] = hour;
        bytes[1] = minute;
        bytes[2] = mSpeed;
        bytes[3] = (byte)(mBearing >> 8);
        bytes[4] = (byte)(mBearing & 0xff);
        // bytes[5] is not implemented
        // bytes[6] is not implemented
        bytes[7] = mSatUsed;
        bytes[8] = mSatCount;

        try
        {
            mBtSocket.getOutputStream().write(bytes);
        }
        catch (IOException e)
        {
            Log.w(TAG, Log.getStackTraceString(e));
            close();
        }
    }

    public void close()
    {
        if (mBtSocket == null)
        {
            return;
        }

        try
        {
            mBtSocket.close();
        }
        catch(IOException e)
        {
            Log.w(TAG, Log.getStackTraceString(e));
        }

        mBtSocket = null;
    }
}
