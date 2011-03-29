/*
    Try to get info from GPS
*/
package nz.gen.geek_central.GPSTest;

public class Main extends android.app.Activity
  {

    @Override
    public void onCreate
      (
        android.os.Bundle savedInstanceState
      )
      {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final android.location.LocationManager Locator =
            (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
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
            final android.location.GpsStatus GPS = Locator.getGpsStatus(null);
            System.err.printf
              (
                "GPS status: max sats = %d, time to first fix = %d\n",
                GPS.getMaxSatellites(),
                GPS.getTimeToFirstFix()
              );
          }
        else
          {
            System.err.println("GPSTest: No location service found!");
          } /*if*/
      } /*onCreate*/

  } /*Main*/
