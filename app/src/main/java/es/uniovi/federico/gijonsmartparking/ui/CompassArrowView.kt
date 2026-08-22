package es.uniovi.federico.gijonsmartparking.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.R as MaterialR

/**
 * Freccia della bussola per "trova la mia auto": custom View che estende [View] e si
 * disegna da sola con onDraw(Canvas)/Paint (l'approccio "extending the View class" visto
 * a teoria per la grafica 2D — niente SurfaceView, niente OpenGL, niente Compose canvas).
 *
 * Non fa nessun calcolo di sensori: riceve da fuori (FindMyCarFragment) l'angolo già
 * pronto in [angleDegrees] e si limita a ruotare il disegno. Ogni volta che l'angolo
 * cambia chiamo invalidate() così Android richiama onDraw() al prossimo frame.
 */
class CompassArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Angolo di rotazione della freccia in gradi (0 = punta verso l'alto dello schermo). */
    var angleDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(MaterialR.attr.colorPrimaryContainer)
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(MaterialR.attr.colorPrimary)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(MaterialR.attr.colorPrimary)
        style = Paint.Style.FILL
    }

    private val arrowPath = Path()

    /** Leggo un colore del tema corrente (segue chiaro/scuro), invece di fissarlo a mano. */
    private fun themeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth

        // sfondo circolare + bordo, così la freccia si vede bene su qualsiasi sfondo
        canvas.drawCircle(cx, cy, radius, circlePaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Ruoto il CANVAS invece di ricalcolare i punti del triangolo: la freccia resta
        // sempre disegnata "dritta verso l'alto" nel path, è la rotazione a farla puntare
        // nella direzione giusta.
        canvas.save()
        canvas.rotate(angleDegrees, cx, cy)

        val tip = radius * 0.7f        // quanto la punta si allunga verso l'alto
        val tailY = radius * 0.45f     // quanto la coda scende in basso
        val halfWidth = radius * 0.32f // larghezza della freccia alla base

        arrowPath.reset()
        arrowPath.moveTo(cx, cy - tip)                     // punta
        arrowPath.lineTo(cx + halfWidth, cy + tailY)        // angolo destro della coda
        arrowPath.lineTo(cx, cy + tailY * 0.4f)             // rientro al centro (forma a freccia)
        arrowPath.lineTo(cx - halfWidth, cy + tailY)        // angolo sinistro della coda
        arrowPath.close()

        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }
}
