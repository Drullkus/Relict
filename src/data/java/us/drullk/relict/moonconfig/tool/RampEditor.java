package us.drullk.relict.moonconfig.tool;

import us.drullk.relict.moonconfig.MoonSpriteConfig;

import javax.swing.JColorChooser;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A draggable gradient stop editor: the strip shows the ramp as it will be sampled, with a handle per stop.
 * <p>
 * Drag a handle to move it, double-click to recolour, right-click to delete, click the empty strip to add a
 * stop at that position taking the colour already there.
 */
public class RampEditor extends JPanel {

    private static final int STRIP_HEIGHT = 40;
    private static final int HANDLE_SIZE = 11;
    private static final int MARGIN = 8;

    private final Runnable onChange;
    private List<MoonSpriteConfig.Stop> stops;
    private MoonSpriteConfig.Interpolation interpolation;
    private int dragging = -1;

    public RampEditor(MoonSpriteConfig.Ramp ramp, Runnable onChange) {
        this.onChange = onChange;
        this.stops = new ArrayList<>(ramp.stops());
        this.interpolation = ramp.interpolation();
        this.setPreferredSize(new Dimension(320, STRIP_HEIGHT + HANDLE_SIZE + MARGIN * 2));
        this.setToolTipText("Drag to move, double-click to recolour, right-click to remove, click the strip to add");
        this.installMouseHandling();
    }

    public MoonSpriteConfig.Ramp ramp() {
        return new MoonSpriteConfig.Ramp(this.interpolation, List.copyOf(this.stops));
    }

    public void setInterpolation(MoonSpriteConfig.Interpolation interpolation) {
        this.interpolation = interpolation;
        this.changed();
    }

    private void installMouseHandling() {
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int handle = RampEditor.this.handleAt(event.getX(), event.getY());

                if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON3) {
                    if (handle >= 0 && RampEditor.this.stops.size() > 1) {
                        RampEditor.this.stops.remove(handle);
                        RampEditor.this.changed();
                    }

                    return;
                }

                if (handle >= 0) {
                    if (event.getClickCount() >= 2) {
                        RampEditor.this.recolour(handle);
                    } else {
                        RampEditor.this.dragging = handle;
                    }

                    return;
                }

                if (event.getY() < STRIP_HEIGHT + MARGIN) {
                    RampEditor.this.addStop(RampEditor.this.positionFor(event.getX()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                RampEditor.this.dragging = -1;
            }
        });

        this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (RampEditor.this.dragging < 0) {
                    return;
                }

                MoonSpriteConfig.Stop moved = RampEditor.this.stops.get(RampEditor.this.dragging);
                RampEditor.this.stops.set(RampEditor.this.dragging,
                        new MoonSpriteConfig.Stop(RampEditor.this.positionFor(event.getX()), moved.rgb()));
                RampEditor.this.sortAndTrack();
                RampEditor.this.changed();
            }
        });
    }

    /** Keeps the codec's ordering invariant true while a handle is dragged past its neighbours. */
    private void sortAndTrack() {
        MoonSpriteConfig.Stop held = this.stops.get(this.dragging);
        this.stops.sort(Comparator.comparingDouble(MoonSpriteConfig.Stop::position));
        this.dragging = this.stops.indexOf(held);
    }

    private void addStop(float position) {
        this.stops.add(new MoonSpriteConfig.Stop(position, this.ramp().sample(position)));
        this.stops.sort(Comparator.comparingDouble(MoonSpriteConfig.Stop::position));
        this.changed();
    }

    private void recolour(int index) {
        MoonSpriteConfig.Stop stop = this.stops.get(index);
        Color picked = JColorChooser.showDialog(this, "Stop colour", new Color(stop.rgb()));
        if (picked != null) {
            this.stops.set(index, new MoonSpriteConfig.Stop(stop.position(), picked.getRGB() & 0xFFFFFF));
            this.changed();
        }
    }

    private void changed() {
        this.repaint();
        this.onChange.run();
    }

    private float positionFor(int x) {
        return Math.clamp((x - MARGIN) / (float) Math.max(1, this.getWidth() - MARGIN * 2), 0.0F, 1.0F);
    }

    private int xFor(float position) {
        return MARGIN + Math.round(position * (this.getWidth() - MARGIN * 2));
    }

    private int handleAt(int x, int y) {
        if (y < STRIP_HEIGHT + MARGIN) {
            return -1;
        }

        for (int index = 0; index < this.stops.size(); index++) {
            if (Math.abs(this.xFor(this.stops.get(index).position()) - x) <= HANDLE_SIZE / 2 + 1) {
                return index;
            }
        }

        return -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        MoonSpriteConfig.Ramp ramp = this.ramp();

        for (int x = MARGIN; x < this.getWidth() - MARGIN; x++) {
            g.setColor(new Color(ramp.sample(this.positionFor(x))));
            g.drawLine(x, MARGIN, x, STRIP_HEIGHT + MARGIN - 1);
        }

        g.setColor(Color.DARK_GRAY);
        g.drawRect(MARGIN, MARGIN, this.getWidth() - MARGIN * 2 - 1, STRIP_HEIGHT - 1);

        for (MoonSpriteConfig.Stop stop : this.stops) {
            int x = this.xFor(stop.position());
            int y = STRIP_HEIGHT + MARGIN + HANDLE_SIZE / 2;
            g.setColor(new Color(stop.rgb()));
            g.fillOval(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.0F));
            g.drawOval(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
        }
    }

}
