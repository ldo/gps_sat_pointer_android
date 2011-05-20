package nz.gen.geek_central.GPSTest;
/*
    Global data for GPS Test program
*/

public class Global
  {
    public static boolean GotTimeDiscrepancy = false;
    public static long LastLocationUpdate = 0; /* in system time */
    public static long LastLocationTime = 0; /* in GPS time */
    public static long TimeDiscrepancy = 0;
  } /*Global*/
