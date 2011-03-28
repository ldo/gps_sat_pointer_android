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
                System.err.printf("Provider: %s = %s\n", ProviderName, ThisProvider.getName());
              /* more TBD */
              } /*for*/
          }
        else
          {
            System.err.println("GPSTest: No location service found!");
          } /*if*/
      } /*onCreate*/

  } /*Main*/
