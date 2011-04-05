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
    Vec3f OrientVector = new Vec3f(0.0f, -1.0f, 0.0f);
    SatInfo[] Sats = {};

    public VectorView
      (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
      )
      {
        super(TheContext, TheAttributes);
      } /*VectorView*/

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
      /* not sure what to make of Datum[2]: value goes up to
        about ±90° and then decreases again */
        final float AziCos = FloatMath.cos(OrientAzi);
        final float AziSin = FloatMath.sin(OrientAzi);
        final float ElevCos = FloatMath.cos(OrientElev);
        final float ElevSin = FloatMath.sin(OrientElev);
        OrientVector = new Vec3f
            (
                - AziSin * ElevCos,
                - AziCos * ElevCos,
                ElevSin
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

    @Override
    protected void onDraw
      (
        android.graphics.Canvas Draw
      )
      {
        super.onDraw(Draw);
        Draw.save();
        final float Radius = 100.0f;
        Draw.translate(Radius, Radius);
        Draw.drawArc /* background */
          (
            /*oval =*/ new android.graphics.RectF(-Radius, -Radius, Radius, Radius),
            /*startAngle =*/ 0.0f,
            /*sweepAngle =*/ 360.0f,
            /*useCenter =*/ false,
            /*paint =*/ GraphicsUseful.FillWithColor(0xff0a6d01)
          );
        float YPos = 0.0f; /* debug */
        for (SatInfo ThisSat : Sats)
          {
            Vec3f D; /* satellite direction in phone coordinates */
              {
                final float AziCos = FloatMath.cos(ThisSat.Azimuth);
                final float AziSin = FloatMath.sin(ThisSat.Azimuth);
                final float ElevCos = FloatMath.cos(ThisSat.Elevation);
                final float ElevSin = FloatMath.sin(ThisSat.Elevation);
                D =
                        Mat4f.rotate_align
                          (
                            new Vec3f(0.0f, -1.0f, 0.0f),
                            OrientVector
                          )
                    .xform(
                        new Vec3f
                          (
                              - AziSin * ElevCos,
                              - AziCos * ElevCos,
                              ElevSin
                          )
                      );
                Draw.drawText
                  (
                    String.format
                      (
                        "(%.2f, %.2f, %.2f) from (%.2f, %.2f, %.2f)(%.2f, %.2f) = (%.2f, %.2f, %.2f)",
                        - AziSin * ElevCos, - AziCos * ElevCos, ElevSin,
                        OrientVector.x, OrientVector.y, OrientVector.z,
                        OrientAzi, OrientElev,
                        D.x, D.y, D.z
                      ),
                    - Radius, YPos - Radius,
                    GraphicsUseful.FillWithColor(0x80ffffff)
                  ); /* debug */
                YPos += 12.0f; /* debug */
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
            Draw.drawPath(V, GraphicsUseful.FillWithColor(0xffeedf09));
          } /*for*/
        Draw.restore();
      } /*onDraw*/

  } /*VectorView*/

