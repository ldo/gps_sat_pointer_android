package nz.gen.geek_central.GPSTest;
/*
    Try to get info from GPS
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
    android.widget.ListView SatsListView;
    android.widget.ArrayAdapter<SatItem> SatsList;
    android.widget.TextView Message;
    android.os.Handler RunBG;
    VectorView Graphical;

    SensorManager SensorMan;
    Sensor OrientationSensor;
    android.location.LocationListener LocationChanged;
    android.hardware.SensorEventListener OrientationChanged;
    StatusGetter PosUpdates;
    android.location.GpsStatus LastGPS;
    int LastStatus = -1;
    int NrSatellites = -1;
    long LastUpdate = 0;
    long TimeDiscrepancy = 0;
    static final long TrustInterval = 3600 * 1000;
      /* how long to use a GPS fix to display correction to system time */

    void UpdateMessage()
      {
        if (Locator != null)
          {
            final android.location.GpsStatus GPS = Locator.getGpsStatus(null);
            System.err.printf
              (
                "GPS status: max sats = %d, time to first fix = %d\n",
                GPS.getMaxSatellites(),
                GPS.getTimeToFirstFix()
              );
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
                if (TimeDiscrepancy != 0)
                  {
                  /* pity I can't correct the clock, but SET_TIME permission is
                    only available to "system" apps */
                    Msg.printf
                      (
                        "System time is %s by %dms\n",
                        TimeDiscrepancy > 0 ? "ahead" : "behind",
                        Math.abs(TimeDiscrepancy)
                      );
                  } /*if*/
                  {
                    final long Now = System.currentTimeMillis();
                    if (Now - LastUpdate <= TrustInterval)
                      {
                        Msg.printf
                          (
                            "Current GPS time is %s\n",
                            android.text.format.DateFormat.format
                              (
                                "kk:mm:ss E, dd/MMM/yyyy",
                                Now - TimeDiscrepancy
                              )
                          );
                      } /*if*/
                  }
                if (LastGPS != null)
                  {
                    int GotSats = 0;
                    int UsedSats = 0;
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
                    Graphical.SetSats(LastGPS.getSatellites());
                  } /*if*/
                if (GPSLast != null)
                  {
                    Msg.printf
                      (
                        "Last fix at %s (%s)\n",
                        android.text.format.DateFormat.format
                          (
                            "kk:mm:ss E, dd/MMM/yyyy",
                            GPSLast.getTime()
                          ),
                        TimeUseful.Ago
                          (
                            GPSLast.getTime(),
                            System.currentTimeMillis() - TimeDiscrepancy
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
                        Msg.printf("%.2f", GPSLast.getSpeed());
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
                Message.setText(MessageBuf.toString());
              }
          } /*if*/
      } /*UpdateMessage*/

    class Updater implements Runnable
      {
        public void run()
          {
            UpdateMessage();
            QueueUpdate();
          } /*run*/
      } /*Updater*/

    void QueueUpdate()
      {
        RunBG.postDelayed
          (
            new Updater(),
            System.currentTimeMillis() - LastUpdate <= TrustInterval ?
                1000
            :
                5000
          );
      } /*QueueUpdate*/

    class StatusGetter implements android.location.GpsStatus.Listener
      {

        public void onGpsStatusChanged
          (
            int Event
          )
          {
            LastGPS = Locator.getGpsStatus(LastGPS);
          } /*onGpsStatusChanged*/

      } /*StatusGetter*/

    class Orienter implements android.hardware.SensorEventListener
      {

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
            Graphical.SetOrientation(Event.values);
          } /*onSensorChanged*/

      } /*Orienter*/

    class Navigator implements android.location.LocationListener
      {
        public void onLocationChanged
          (
            Location NewLocation
          )
          {
            LastUpdate = System.currentTimeMillis();
            TimeDiscrepancy = LastUpdate - NewLocation.getTime();
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

      } /*Navigator*/

    @Override
    public void onCreate
      (
        android.os.Bundle savedInstanceState
      )
      {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        SatsListView = (android.widget.ListView)findViewById(R.id.sats_list);
        SatsList = new android.widget.ArrayAdapter<SatItem>
          (
            this,
            R.layout.satellite_listitem
          );
        SatsListView.setAdapter(SatsList);
        Message = (android.widget.TextView)findViewById(R.id.message);
        Graphical = (VectorView)findViewById(R.id.vector_view);
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
                        Graphical.FlashSat(((SatItem)SatsList.getItem(position)).Prn);
                      } /*if*/
                  } /*OnItemSelected*/

              }
          );
        Locator = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (Locator != null)
          {
            for (String ProviderName : Locator.getProviders(false))
              {
                final android.location.LocationProvider ThisProvider =
                    Locator.getProvider(ProviderName);
                System.err.printf
                  (
                    "Provider %s: accuracy = %d, powerreq = %d, costs = %s\n",
                    ProviderName,
                    ThisProvider.getAccuracy(),
                    ThisProvider.getPowerRequirement(),
                    ThisProvider.hasMonetaryCost()
                  );
                System.err.printf
                  (
                    " req cell = %s, req net = %s, req sat = %s\n",
                    ThisProvider.requiresCell(),
                    ThisProvider.requiresNetwork(),
                    ThisProvider.requiresSatellite()
                  );
                System.err.printf
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
                    System.err.printf
                      (
                        " last known location: %s\n",
                        Dump.toString()
                      );
                  }
                else
                  {
                    System.err.println(" no last known location");
                  } /*if*/
              } /*for*/
          }
        else
          {
            System.err.println("GPSTest: No location service found!");
          } /*if*/
        SensorMan = ((SensorManager)getSystemService(SENSOR_SERVICE));
        OrientationSensor = SensorMan.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        UpdateMessage();
        RunBG = new android.os.Handler();
        QueueUpdate();
        LocationChanged = new Navigator();
        OrientationChanged = new Orienter();
        PosUpdates = new StatusGetter();
      } /*onCreate*/

    @Override
    public void onPause()
      {
        super.onPause();
      /* conserve battery: */
        if (OrientationSensor != null)
          {
            SensorMan.unregisterListener(OrientationChanged, OrientationSensor);
          } /*if*/
        Locator.removeGpsStatusListener(PosUpdates);
        Locator.removeUpdates(LocationChanged);
      } /*onPause*/

    @Override
    public void onResume()
      {
        super.onResume();
        Locator.addGpsStatusListener(PosUpdates);
        Locator.requestLocationUpdates
          (
            /*provider =*/ LocationManager.GPS_PROVIDER,
            /*minTime =*/ 10 * 1000,
            /*minDistance =*/ 0,
            /*listener =*/ LocationChanged
          );
        if (OrientationSensor != null)
          {
            SensorMan.registerListener
              (
                OrientationChanged,
                OrientationSensor,
                SensorManager.SENSOR_DELAY_UI
              );
          } /*if*/
      } /*onResume*/

  } /*Main*/
