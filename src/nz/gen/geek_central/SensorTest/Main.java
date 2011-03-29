/*
    Try to get info from GPS
*/
package nz.gen.geek_central.GPSTest;

public class Main extends android.app.Activity
  {
    android.location.LocationManager Locator;
    android.widget.TextView Message;
    android.os.Handler RunBG;
    android.location.LocationListener LetMeKnow;
    android.location.GpsStatus LastGPS;
    int LastStatus = -1;
    int NrSatellites = -1;
    java.util.Set<String> BundleKeys = null;

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
                final android.location.Location GPSLast =
                    Locator.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                final java.io.ByteArrayOutputStream MessageBuf =
                    new java.io.ByteArrayOutputStream();
                final java.io.PrintStream Msg = new java.io.PrintStream(MessageBuf);
                Msg.printf
                  (
                    "GPS enabled: %s.\n",
                    Locator.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                  );
                if (LastGPS != null)
                  {
                    int GotSats = 0;
                    for (android.location.GpsSatellite ThisSat : LastGPS.getSatellites())
                      {
                        ++GotSats;
                        Msg.printf
                          (
                            "Sat %d azi %.0f° elev %.0f° prn %d snr %.2fdB almanac %s ephemeris %s used %s\n",
                            GotSats,
                            ThisSat.getAzimuth(), /* returned only to nearest degree */
                            ThisSat.getElevation(), /* returned only to nearest degree */
                            ThisSat.getPrn(),
                            ThisSat.getSnr(),
                            ThisSat.hasAlmanac(),
                            ThisSat.hasEphemeris(),
                            ThisSat.usedInFix()
                          );
                      } /*for*/
                    Msg.printf("Sats found: %d\n", GotSats);
                  } /*if*/
                if (GPSLast != null)
                  {
                    Msg.printf
                      (
                        "Last GPS fix at %s\n",
                        android.text.format.DateFormat.format
                          (
                            "kk:mm:ss E, dd/MMM/yyyy",
                            GPSLast.getTime()
                          )
                      );
                    Msg.printf
                      (
                        "Lat %.6f°, Long %.6f°\n",
                        GPSLast.getLatitude(),
                        GPSLast.getLongitude()
                      );
                    Msg.print("Accuracy: ");
                    if (GPSLast.hasAccuracy())
                      {
                        Msg.printf("±%.2fm", GPSLast.getAccuracy());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.println();
                    Msg.printf
                      (
                        "Status: %d, nr satellites: %d\n",
                        LastStatus,
                        NrSatellites
                      );
                    Msg.print("Last bundle keys: ");
                    if (BundleKeys != null)
                      {
                        boolean First = true;
                        for (String Key : BundleKeys.toArray(new String[BundleKeys.size()]))
                          {
                            if (!First)
                              {
                                Msg.print(", ");
                              } /*if*/
                            First = false;
                            Msg.print(Key);
                          } /*for*/
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.println();
                    Msg.print("Altitude: ");
                    if (GPSLast.hasAltitude())
                      {
                        Msg.printf("%.1fm", GPSLast.getAltitude());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.println();
                    Msg.print("Speed: ");
                    if (GPSLast.hasSpeed())
                      {
                        Msg.printf("%.2f", GPSLast.getSpeed());
                      }
                    else
                      {
                        Msg.print("N/A");
                      } /*if*/
                    Msg.println();
                    Msg.print("Bearing: ");
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
        RunBG.postDelayed(new Updater(), 60 * 1000);
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

    class Navigator implements android.location.LocationListener
      {
        public void onLocationChanged
          (
            android.location.Location NewLocation
          )
          {
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
                BundleKeys = Extras.keySet();
                NrSatellites = Extras.getInt("satellites");
              }
            else
              {
                BundleKeys = null;
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
        Locator = (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
        Message = (android.widget.TextView)findViewById(R.id.message);
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
                final android.location.Location LastKnown =
                    Locator.getLastKnownLocation(ProviderName);
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
      /* UpdateMessage(); */ /* check QueueUpdate works */
        RunBG = new android.os.Handler();
        QueueUpdate();
        LetMeKnow = new Navigator();
        Locator.addGpsStatusListener(new StatusGetter());
        Locator.requestLocationUpdates
          (
            /*provider =*/ android.location.LocationManager.GPS_PROVIDER,
            /*minTime =*/ 60 * 1000,
            /*minDistance =*/ 0,
            /*listener =*/ LetMeKnow
          );
      } /*onCreate*/

  } /*Main*/
