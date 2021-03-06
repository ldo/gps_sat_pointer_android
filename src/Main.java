package nz.gen.geek_central.GPSSatPointer;
/*
    Example of getting location and satellite info from GPS.

    Copyright 2011, 2012 by Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
*/

import android.location.Location;
import android.location.LocationManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;

class TimeUseful
  {
    protected static class AgoStep
      {
        public int Divider;
        public String Singular, Plural;

        public AgoStep
          (
            int Divider,
            String Singular,
            String Plural
          )
          {
            this.Divider = Divider;
            this.Singular = Singular;
            this.Plural = Plural;
          } /*AgoStep*/

      } /*AgoStep*/
    protected static AgoStep[] AgoSteps =
        {
            new AgoStep(1, "second", "seconds"),
            new AgoStep(60, "minute", "minutes"),
            new AgoStep(60, "hour", "hours"),
            new AgoStep(24, "day", "days"),
            new AgoStep(7, "week", "weeks"),
        };

    public static String Ago
      (
        long Then,
        long Now
      )
      /* given two times in milliseconds since the epoch, returns a string
        saying how long ago Then was compared to Now. */
      {
        long interval = (Now - Then) / 1000;
        AgoStep unit = null; /* won't be left null */
        for (int unitindex = 0;;)
          {
            if (unitindex == AgoSteps.length)
                break;
            unit = AgoSteps[unitindex];
            if (interval < unit.Divider)
              {
                unit = AgoSteps[Math.max(unitindex - 1, 0)];
                break;
              } /*if*/
            interval /= unit.Divider;
            ++unitindex;
          } /*for*/
        return
            String.format
              (
                "%d %s ago",
                interval,
                interval != 1 ? unit.Plural : unit.Singular
              );
      } /*Ago*/

  } /*TimeUseful*/

public class Main extends android.app.Activity
  {
    class SatItem
      {
        String Info;
        int Prn;

        public SatItem
          (
            String Info,
            int Prn
          )
          {
            this.Info = Info;
            this.Prn = Prn;
          } /*SatItem*/

        public String toString()
          {
            return Info;
          } /*toString*/

        public int GetPrn()
          {
            return Prn;
          } /*GetPrn*/

      } /*SatItem*/

    LocationManager Locator;
    VectorView Vectors;
    android.widget.ListView SatsListView;
    android.widget.ArrayAdapter<SatItem> SatsList;
    android.widget.TextView Message1, Message2;
    android.os.Handler RunBG;
    CommonListener Listen;

    SensorManager SensorMan;
    Sensor OrientationSensor;
    android.location.GpsStatus LastGPS;
    int LastStatus = -1;
    int NrSatellites = -1;
    static final long TrustInterval = 3600 * 1000;
      /* how long to use a GPS fix to display correction to system time */

    GetTimeOffset TimeOffsetResponder;

    private class CommonListener
        implements
            android.location.GpsStatus.Listener,
            android.location.LocationListener,
            android.hardware.SensorEventListener
      {
        android.os.Handler RunTask;
        Runnable NextUnflash = null;

        public CommonListener()
          {
            RunTask = new android.os.Handler();
          } /*CommonListener*/

        public void Start()
          {
            Locator.addGpsStatusListener(this);
            Locator.requestLocationUpdates
              (
                /*provider =*/ LocationManager.GPS_PROVIDER,
                /*minTime =*/ 10 * 1000,
                /*minDistance =*/ 0,
                /*listener =*/ this
              );
            if (OrientationSensor != null)
              {
                SensorMan.registerListener
                  (
                    this,
                    OrientationSensor,
                    SensorManager.SENSOR_DELAY_UI
                  );
              } /*if*/
          } /*Start*/

        public void Stop()
          {
          /* conserve battery: */
            if (OrientationSensor != null)
              {
                SensorMan.unregisterListener(this, OrientationSensor);
              } /*if*/
            Locator.removeGpsStatusListener(this);
            Locator.removeUpdates(this);
          } /*Stop*/

        public void Finish()
          {
            Stop();
          } /*Finish*/

        public void SetSats
          (
            Iterable<android.location.GpsSatellite> Sats
          )
          /* specifies a new set of satellite data to display. */
          {
            Vectors.SetSats(Sats);
          } /*SetSats*/

        class FlashResetter implements Runnable
          {
            boolean DidRun;

            public FlashResetter()
              {
                DidRun = false;
              } /*FlashResetter*/

            public void run()
              {
                if (!DidRun)
                  {
                    Vectors.SetFlashPrn(-1); /* clear highlight */
                    DidRun = true;
                  } /*if*/
              } /*run*/

          } /*FlashResetter*/

        public void FlashSat
          (
            int Prn
          )
          /* temporarily highlight the part of the graphic representing the specified satellite. */
          {
            if (NextUnflash != null)
              {
                RunTask.removeCallbacks(NextUnflash);
                NextUnflash.run();
                NextUnflash = null;
              } /*if*/
            Vectors.SetFlashPrn(Prn);
            NextUnflash = new FlashResetter();
            RunTask.postDelayed(NextUnflash, 500);
          } /*FlashSat*/

      /* LocationListener methods */
        public void onLocationChanged
          (
            Location NewLocation
          )
          {
            Global.LastLocationUpdate = System.currentTimeMillis();
            Global.LastLocationTime = NewLocation.getTime();
            Global.TimeDiscrepancy = Global.LastLocationUpdate - Global.LastLocationTime;
            Global.GotTimeDiscrepancy = true;
            UpdateMessage();
          } /*onLocationChanged*/

        public void onProviderDisabled
          (
            String ProviderName
          )
          {
            UpdateMessage();
          } /*onProviderDisabled*/

        public void onProviderEnabled
          (
            String ProviderName
          )
          {
            UpdateMessage();
          } /*onProviderEnabled*/

        public void onStatusChanged
          (
            String ProviderName,
            int Status,
            android.os.Bundle Extras
          )
          {
            LastStatus = Status;
            if (Extras != null)
              {
                NrSatellites = Extras.getInt("satellites");
              }
            else
              {
                NrSatellites = -1;
              } /*if*/
            UpdateMessage();
          } /*onStatusChanged*/

      /* GpsStatus.Listener methods */
        public void onGpsStatusChanged
          (
            int Event
          )
          {
            LastGPS = Locator.getGpsStatus(LastGPS);
          } /*onGpsStatusChanged*/

      /* SensorEventListener methods */
        public void onAccuracyChanged
          (
            Sensor TheSensor,
            int NewAccuracy
          )
          {
          /* don't care */
          } /*onAccuracyChanged*/

        public void onSensorChanged
          (
            android.hardware.SensorEvent Event
          )
          {
            Vectors.SetOrientation(Event.values);
          } /*onSensorChanged*/

      } /*CommonListener*/

    void UpdateMessage()
      {
        if (Locator != null)
          {
            final android.location.GpsStatus GPS = Locator.getGpsStatus(null);
              {
                final Location GPSLast =
                    Locator.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                final java.io.ByteArrayOutputStream MessageBuf =
                    new java.io.ByteArrayOutputStream();
                final java.io.PrintStream Msg = new java.io.PrintStream(MessageBuf);
                Msg.printf
                  (
                    "GPS enabled: %s.\n",
                    Locator.isProviderEnabled(LocationManager.GPS_PROVIDER)
                  );
                Msg.printf
                  (
                    "GPS status: max sats = %d, time to first fix = %d\n",
                    GPS.getMaxSatellites(),
                    GPS.getTimeToFirstFix()
                  );
                if (Global.GotTimeDiscrepancy)
                  {
                  /* pity I can't correct the clock, but SET_TIME permission is
                    only available to "system" apps */
                    if (Global.TimeDiscrepancy != 0)
                      {
                        Msg.printf
                          (
                            "System time is %s by %dms\n",
                            Global.TimeDiscrepancy > 0 ? "ahead" : "behind",
                            Math.abs(Global.TimeDiscrepancy)
                          );
                      }
                    else
                      {
                        Msg.println("System time is correct to the millisecond!"); /* yeah, right */
                      } /*if*/
                  } /*if*/
                  {
                    final long Now = System.currentTimeMillis();
                    if
                      (
                            Global.GotTimeDiscrepancy
                        &&
                            Now - Global.LastLocationUpdate <= TrustInterval
                      )
                      {
                        Msg.printf
                          (
                            "Current GPS time is %s\n",
                            android.text.format.DateFormat.format
                              (
                                "kk:mm:ss E, dd/MMM/yyyy z",
                                Now - Global.TimeDiscrepancy
                              )
                          );
                      } /*if*/
                  }
                if (LastGPS != null)
                  {
                    int GotSats = 0;
                    int UsedSats = 0;
                    SatsList.setNotifyOnChange(false);
                    SatsList.clear();
                    for (android.location.GpsSatellite ThisSat : LastGPS.getSatellites())
                      {
                        ++GotSats;
                        if (ThisSat.usedInFix())
                          {
                            ++UsedSats;
                          } /*if*/
                        SatsList.add
                          (
                            new SatItem
                              (
                                String.format
                                  (
                                    "Prn %d azi %.0f° elev %.0f° snr %.1fdB used=%s",
                                    ThisSat.getPrn(),
                                    ThisSat.getAzimuth(), /* returned only to nearest degree */
                                    ThisSat.getElevation(), /* returned only to nearest degree */
                                    ThisSat.getSnr(), /* only to one decimal place */
                                  /* ThisSat.hasAlmanac() and ThisSat.hasEphemeris()
                                    always seem to be true */
                                    ThisSat.usedInFix() ? "Y" : "N"
                                  ),
                                ThisSat.getPrn()
                              )
                          );
                      } /*for*/
                    Msg.printf("Sats used/found: %d/%d\n", UsedSats, GotSats);
                    SatsList.notifyDataSetChanged();
                    Listen.SetSats(LastGPS.getSatellites());
                  } /*if*/
                if (GPSLast != null)
                  {
                    Msg.printf
                      (
                        "Last fix at %s (%s)\n",
                        android.text.format.DateFormat.format
                          (
                            "kk:mm:ss E, dd/MMM/yyyy z",
                            GPSLast.getTime()
                          ),
                        TimeUseful.Ago
                          (
                            GPSLast.getTime(),
                            System.currentTimeMillis() - Global.TimeDiscrepancy
                          )
                      );
                    Msg.printf
                      (
                        "Lat %.6f°, Long %.6f°",
                        GPSLast.getLatitude(),
                        GPSLast.getLongitude()
                      );
                    if (GPSLast.hasAccuracy())
                      {
                        Msg.printf(" ±%.2fm", GPSLast.getAccuracy());
                      } /*if*/
                    Msg.println();
                    Msg.printf
                      (
                        "Status: %d, nr satellites: %d\n",
                        LastStatus,
                        NrSatellites
                      );
                    Msg.print("Altitude: ");
                    if (GPSLast.hasAltitude())
                      {
                        Msg.printf("%.1fm", GPSLast.getAltitude());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.print(", Speed: ");
                    if (GPSLast.hasSpeed())
                      {
                        Msg.printf("%.2fm/s", GPSLast.getSpeed());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.print(", Bearing: ");
                    if (GPSLast.hasBearing())
                      {
                        Msg.printf("%.2f°", GPSLast.getBearing());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.println();
                  }
                else
                  {
                    Msg.println("No last known GPS location.");
                  } /*if*/
                Msg.flush();
                Message1.setText(MessageBuf.toString());
              }
          } /*if*/
      } /*UpdateMessage*/

    class Updater implements Runnable
      {
        public void run()
          {
            UpdateMessage();
          /* fixme: stop it running when I'm not active */
            QueueUpdate();
          } /*run*/
      } /*Updater*/

    void QueueUpdate()
      {
        RunBG.postDelayed
          (
            new Updater(),
            System.currentTimeMillis() - Global.LastLocationUpdate <= TrustInterval ?
                1000
            :
                5000
          );
      } /*QueueUpdate*/

    @Override
    public void onCreate
      (
        android.os.Bundle savedInstanceState
      )
      {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Vectors = (VectorView)findViewById(R.id.vector_view);
        Vectors.Rotation = getWindowManager().getDefaultDisplay().getRotation();
        SatsListView = (android.widget.ListView)findViewById(R.id.sats_list);
        SatsList = new android.widget.ArrayAdapter<SatItem>
          (
            this,
            R.layout.satellite_listitem
          );
        SatsListView.setAdapter(SatsList);
        Message1 = (android.widget.TextView)findViewById(R.id.message1);
        Message2 = (android.widget.TextView)findViewById(R.id.message2);
        Listen = new CommonListener();
        SatsListView.setOnItemClickListener
          (
            new android.widget.AdapterView.OnItemClickListener()
              {

                public void onItemClick
                  (
                    android.widget.AdapterView<?> parent,
                    android.view.View view,
                    int position,
                    long id
                  )
                  {
                    if (position >= 0 && position < SatsList.getCount())
                      {
                      /* flash the part of the graphic representing the selected satellite */
                        Listen.FlashSat((SatsList.getItem(position)).Prn);
                      } /*if*/
                  } /*OnItemSelected*/

              }
          );
        Locator = (LocationManager)getSystemService(LOCATION_SERVICE);
          {
            final java.io.ByteArrayOutputStream MessageBuf =
                new java.io.ByteArrayOutputStream();
            final java.io.PrintStream Msg = new java.io.PrintStream(MessageBuf);
            if (Locator != null)
              {
                for (String ProviderName : Locator.getProviders(false))
                  {
                    final android.location.LocationProvider ThisProvider =
                        Locator.getProvider(ProviderName);
                    Msg.printf
                      (
                        "Provider %s: accuracy = %d, powerreq = %d, costs = %s\n",
                        ProviderName,
                        ThisProvider.getAccuracy(),
                        ThisProvider.getPowerRequirement(),
                        ThisProvider.hasMonetaryCost()
                      );
                    Msg.printf
                      (
                        " req cell = %s, req net = %s, req sat = %s\n",
                        ThisProvider.requiresCell(),
                        ThisProvider.requiresNetwork(),
                        ThisProvider.requiresSatellite()
                      );
                    Msg.printf
                      (
                        " does altitude = %s, does bearing = %s, does speed = %s\n",
                        ThisProvider.supportsAltitude(),
                        ThisProvider.supportsBearing(),
                        ThisProvider.supportsSpeed()
                      );
                    final Location LastKnown = Locator.getLastKnownLocation(ProviderName);
                    if (LastKnown != null)
                      {
                        final StringBuilder Dump = new StringBuilder();
                        LastKnown.dump(new android.util.StringBuilderPrinter(Dump), "\n  loc:");
                        Msg.printf
                          (
                            " last known location: %s\n",
                            Dump.toString()
                          );
                      }
                    else
                      {
                        Msg.println(" no last known location");
                      } /*if*/
                  } /*for*/
              }
            else
              {
                Msg.println("No location service found!");
              } /*if*/
            Message2.setText(MessageBuf.toString());
          }
        SensorMan = (SensorManager)getSystemService(SENSOR_SERVICE);
        OrientationSensor = SensorMan.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        UpdateMessage();
        RunBG = new android.os.Handler();
        QueueUpdate();
      /* explicitly register broadcast receiver here rather than in manifest,
        because it cannot return any meaningful data if app is not running */
        TimeOffsetResponder = new GetTimeOffset();
        registerReceiver
          (
            /*receiver =*/ TimeOffsetResponder,
            /*filter =*/ new android.content.IntentFilter("nz.gen.geek_central.gps.GET_TIME_OFFSET")
          );
      } /*onCreate*/

    @Override
    public void onDestroy()
      {
        unregisterReceiver(TimeOffsetResponder);
        Listen.Finish();
        super.onDestroy();
      } /*onDestroy*/

    @Override
    public void onPause()
      {
        Vectors.onPause();
        Listen.Stop();
        super.onPause();
      } /*onPause*/

    @Override
    public void onResume()
      {
        super.onResume();
        Listen.Start();
        Vectors.onResume();
      } /*onResume*/

  } /*Main*/
