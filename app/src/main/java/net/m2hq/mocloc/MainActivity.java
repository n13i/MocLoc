package net.m2hq.mocloc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

        if ("net.m2hq.mocloc.action.STOP_SERVICE".equals(getIntent().getAction()))
        {
            stopService(new Intent(getApplication(), MockLocationService.class));
        }
        else
        {
            if (!isServiceRunning())
            {
                startService(new Intent(getApplication(), MockLocationService.class));
            }
            else
            {
                stopService(new Intent(getApplication(), MockLocationService.class));
            }
        }

        finish();
    }

    private boolean isServiceRunning()
    {
        ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (MockLocationService.class.getName().equals(serviceInfo.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }
}
