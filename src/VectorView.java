package nz.gen.geek_central.GPSTest;
/*
    Graphical plot of satellite location
*/

import android.util.FloatMath;

class GraphicsUseful
  {
    public final static float Pi = (float)Math.PI;

    public static float ToRadians
      (
        float Degrees
      )
      {
        return Pi * Degrees / 180.0f;
      } /*ToRadians*/

    public static float ToDegrees
      (
        float Radians
      )
      {
        return 180.0f * Radians / Pi;
      } /*ToDegrees*/

    public static android.graphics.Paint FillWithColor
      (
        int TheColor
      )
      /* returns a Paint that will fill with a solid colour. */
      {
        final android.graphics.Paint ThePaint = new android.graphics.Paint();
        ThePaint.setStyle(android.graphics.Paint.Style.FILL);
        ThePaint.setColor(TheColor);
        return
            ThePaint;
      } /*FillWithColor*/

  } /*GraphicsUseful*/

public class VectorView extends android.view.View
  {
    class SatInfo
      {
      /* all the GpsSatellite info I care about */
        final float Azimuth, Elevation; /* radians */
        final int Prn; /* unique satellite id? Could use to colour-code or something */

        public SatInfo
          (
            float Azimuth, /* degrees */
            float Elevation, /* degrees */
            int Prn
          )
          {
            this.Azimuth = GraphicsUseful.ToRadians(Azimuth);
            this.Elevation = GraphicsUseful.ToRadians(Elevation);
            this.Prn = Prn;
          } /*SatInfo*/

      } /*SatInfo*/

    float OrientAzi = 0.0f;
    float OrientElev = 0.0f;
    float OrientRoll = 0.0f;
    Mat4f OrientMatrix = Mat4f.identity();
    SatInfo[] Sats = {};
    android.os.Handler RunTask;
    int FlashPrn = -1;
    Runnable NextUnflash = null;

    public VectorView
      (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
      )
      {
        super(TheContext, TheAttributes);
        RunTask = new android.os.Handler();
      } /*VectorView*/

    class FlashResetter implements Runnable
      {
        VectorView Parent;
        boolean DidRun;

        public FlashResetter
          (
            VectorView Parent
          )
          {
            this.Parent = Parent;
            DidRun = false;
          } /*FlashResetter*/

        public void run()
          {
            if (!DidRun)
              {
                Parent.FlashPrn = -1; /* clear highlight */
                Parent.invalidate();
                DidRun = true;
              } /*if*/
          } /*run*/

      } /*FlashResetter*/

    public void SetOrientation
      (
        float[] Datum /* 3 values from orientation sensor */
      )
      /* sets reference orientation for computing satellite directions. */
      {
      /* work this out myself--SensorManager.getRotationMatrixFromVector
        isn't available before Android 2.3, API level 9 */
        OrientAzi = GraphicsUseful.ToRadians(Datum[0]);
        OrientElev = GraphicsUseful.ToRadians(Datum[1]);
        OrientRoll = GraphicsUseful.ToRadians(Datum[2]);
        OrientMatrix =
                Mat4f.rotation(Mat4f.AXIS_Z, - OrientAzi)
            .mul(
                Mat4f.rotation(Mat4f.AXIS_X, - OrientElev)
            ).mul(
                Mat4f.rotation(Mat4f.AXIS_Y, OrientRoll)
            );
        invalidate();
      } /*SetOrientation*/

    public void SetSats
      (
        Iterable<android.location.GpsSatellite> Sats
      )
      /* specifies a new set of satellite data to display. */
      {
        java.util.ArrayList<SatInfo> NewSats = new java.util.ArrayList<SatInfo>();
        for (android.location.GpsSatellite ThisSat : Sats)
          {
            NewSats.add
              (
                new SatInfo(ThisSat.getAzimuth(), ThisSat.getElevation(), ThisSat.getPrn())
              );
          } /*for*/
        this.Sats = NewSats.toArray(new SatInfo[NewSats.size()]);
        invalidate();
      } /*SetSats*/

    public void FlashSat
      (
        int Prn
      )
      /* temporarily highlight the part of the graphic representing the specified satellite. */
      {
        if (NextUnflash != null)
          {
          /* Note there might be a race condition here! Not that it matters for what
            NextUnflash does. */
            RunTask.removeCallbacks(NextUnflash);
            NextUnflash.run();
            NextUnflash = null;
          } /*if*/
        FlashPrn = Prn;
        NextUnflash = new FlashResetter(this);
        RunTask.postDelayed(NextUnflash, 250);
        invalidate();
      } /*FlashSat*/

    @Override
    protected void onDraw
      (
        android.graphics.Canvas Draw
      )
      {
        super.onDraw(Draw);
        Draw.save();
        final float Radius = (float)Math.min(getWidth(), getHeight()) / 2.0f;
        Draw.translate(Radius, Radius);
        Draw.drawArc /* background */
          (
            /*oval =*/ new android.graphics.RectF(-Radius, -Radius, Radius, Radius),
            /*startAngle =*/ 0.0f,
            /*sweepAngle =*/ 360.0f,
            /*useCenter =*/ false,
            /*paint =*/ GraphicsUseful.FillWithColor(0xff0a6d01)
          );
        for (SatInfo ThisSat : Sats)
          {
            Vec3f D; /* satellite direction in phone coordinates */
              {
                final float AziCos = FloatMath.cos(ThisSat.Azimuth);
                final float AziSin = FloatMath.sin(ThisSat.Azimuth);
                final float ElevCos = FloatMath.cos(ThisSat.Elevation);
                final float ElevSin = FloatMath.sin(ThisSat.Elevation);
                D =
                    OrientMatrix.xform
                      (
                        new Vec3f
                          (
                              AziSin * ElevCos,
                              - AziCos * ElevCos,
                              ElevSin
                          )
                      );
              }
            final android.graphics.Path V = new android.graphics.Path();
            final float BaseWidth = 5.0f;
            final float EndWidth = BaseWidth * (1.0f + D.z);
              /* taper to simulate perspective foreshortening */
            V.moveTo(0.0f, 0.0f);
            V.lineTo(+ BaseWidth * D.y, - BaseWidth * D.x);
            V.lineTo
              (
                + EndWidth * D.y + Radius * D.x,
                - EndWidth * D.x + Radius * D.y
              );
            V.lineTo
              (
                - EndWidth * D.y + Radius * D.x,
                + EndWidth * D.x + Radius * D.y
              );
            V.lineTo(- BaseWidth * D.y, + BaseWidth * D.x);
            V.close();
            Draw.drawPath
              (
                V,
                GraphicsUseful.FillWithColor
                  (
                    ThisSat.Prn == FlashPrn ?
                        0xffce15ee
                    :
                        0xffeedf09
                  )
              );
          } /*for*/
        Draw.restore();
      } /*onDraw*/

  } /*VectorView*/

