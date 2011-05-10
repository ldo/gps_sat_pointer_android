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
        ThePaint.setAntiAlias(true);
        return
            ThePaint;
      } /*FillWithColor*/

    public static void DrawCenteredText
      (
        android.graphics.Canvas Draw,
        String TheText,
        android.graphics.PointF Where,
        android.graphics.Paint UsePaint
      )
      /* draws text at position x, vertically centred around y. */
      {
        final android.graphics.Rect TextBounds = new android.graphics.Rect();
        UsePaint.getTextBounds(TheText, 0, TheText.length(), TextBounds);
        Draw.drawText
          (
            TheText,
            Where.x, /* depend on UsePaint to align horizontally */
            Where.y - (TextBounds.bottom + TextBounds.top) / 2.0f,
            UsePaint
          );
      } /*DrawCenteredText*/

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

    Vec3f OurDirection
      (
        float Azimuth,
        float Elevation
      )
     /* returns direction vector corresponding to Azimuth and Elevation in phone coordinates. */
      {
        Vec3f D;
        final float AziCos = FloatMath.cos(Azimuth);
        final float AziSin = FloatMath.sin(Azimuth);
        final float ElevCos = FloatMath.cos(Elevation);
        final float ElevSin = FloatMath.sin(Elevation);
        D = OrientMatrix.xform
          (
            new Vec3f
              (
                  AziSin * ElevCos,
                  - AziCos * ElevCos,
                  ElevSin
              )
          );
        return 
            D;
      } /*OurDirection*/

    android.graphics.Path PointTo
      (
        float Azimuth,
        float Elevation,
        float Radius,
        boolean DoubleLength
          /* true to extend past other side of origin, false to start from origin */
      )
      /* returns an arrow path pointing in the specified absolute
        direction adjusted for phone coordinates. */
      {
        final Vec3f D = OurDirection(Azimuth, Elevation);
        final android.graphics.Path V = new android.graphics.Path();
        final float Widen = 1.0f + D.z;
          /* taper to simulate perspective foreshortening */
        final float BaseWidth = 5.0f;
        final float EndWidth = BaseWidth * Widen;
        final float ArrowLength = 10.0f;
        final float ArrowWidthExtra = 5.0f * Widen;
        if (DoubleLength)
          {
            V.moveTo(0, - Radius);
            V.lineTo(+ BaseWidth * (1.0f - D.z), - Radius);
          }
        else
          {
            V.moveTo(0, 0);
            V.lineTo(+ BaseWidth, 0);
          } /*if*/
        V.lineTo(+ EndWidth, + Radius - ArrowLength);
        V.lineTo(+ EndWidth + ArrowWidthExtra, + Radius - ArrowLength);
        V.lineTo(0, + Radius);
        V.lineTo(- EndWidth - ArrowWidthExtra, + Radius - ArrowLength);
        V.lineTo(- EndWidth, + Radius - ArrowLength);
        if (DoubleLength)
          {
            V.lineTo(- BaseWidth * (1.0f - D.z), - Radius);
          }
        else
          {
            V.lineTo(- BaseWidth, 0);
          } /*if*/
        V.close();
        final android.graphics.Matrix Orient = new android.graphics.Matrix();
        Orient.postScale(1.0f, (float)Math.hypot(D.x, D.y));
          /* perspective foreshortening factor */
        Orient.postRotate(GraphicsUseful.ToDegrees((float)Math.atan2(- D.x, D.y)));
        V.transform(Orient);
        return
            V;
      } /*PointTo*/

    android.graphics.PointF PointAt
      (
        float Azimuth,
        float Elevation,
        float Radius
      )
      /* returns the coordinates where PointTo places the tip of the arrow. */
      {
        final Vec3f D = OurDirection(Azimuth, Elevation);
        final float[] Result = {0.0f, + Radius};
        final android.graphics.Matrix Orient = new android.graphics.Matrix();
        Orient.postScale(1.0f, (float)Math.hypot(D.x, D.y));
          /* perspective foreshortening factor */
        Orient.postRotate(GraphicsUseful.ToDegrees((float)Math.atan2(- D.x, D.y)));
        Orient.mapPoints(Result);
        return
            new android.graphics.PointF(Result[0], Result[1]);
      } /*PointAt*/

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
        final android.graphics.Paint TextPaint = GraphicsUseful.FillWithColor(0xff887f04);
        TextPaint.setTextSize(28.0f);
        TextPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        TextPaint.setAntiAlias(true);
        Draw.drawPath /* show direction of north */
          (
            PointTo(0.0f, 0.0f, Radius, true),
            GraphicsUseful.FillWithColor(0xff48c1af)
          );
        GraphicsUseful.DrawCenteredText
          (
            /*Draw =*/ Draw,
            /*TheText =*/ "N",
            /*Where =*/ PointAt(0.0f, 0.0f, Radius),
            /*UsePaint =*/ TextPaint
          );
        for (SatInfo ThisSat : Sats)
          {
            Draw.drawPath
              (
                PointTo(ThisSat.Azimuth, ThisSat.Elevation, Radius, false),
                GraphicsUseful.FillWithColor
                  (
                    ThisSat.Prn == FlashPrn ?
                        0xffce15ee
                    :
                        0xffeedf09
                  )
              );
            GraphicsUseful.DrawCenteredText
              (
                /*Draw =*/ Draw,
                /*TheText =*/ String.format("%d", ThisSat.Prn),
                /*Where =*/ PointAt(ThisSat.Azimuth, ThisSat.Elevation, Radius),
                /*UsePaint =*/ TextPaint
              );
          } /*for*/
        Draw.restore();
      } /*onDraw*/

  } /*VectorView*/
