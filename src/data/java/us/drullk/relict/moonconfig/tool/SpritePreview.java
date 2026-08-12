package us.drullk.relict.moonconfig.tool;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

/**
 * Shows one frame large plus the whole filmstrip, composited the way the game will: added onto a sky colour.
 * <p>
 * Judging an additively blended sprite on a neutral background is judging the wrong thing — a glow that
 * reads beautifully at midnight can vanish against a bright butterscotch noon — so the backdrop matters as
 * much as the sprite.
 */
public class SpritePreview extends JPanel {

    private static final int LARGE_SCALE = 12;
    private static final int STRIP_SCALE = 3;
    private static final int GAP = 12;

    private List<BufferedImage> frames = List.of();
    private int selected;
    private int backdrop = 0x000000;
    private Set<Integer> duplicates = Set.of();

    public SpritePreview() {
        this.setPreferredSize(new Dimension(560, 520));
    }

    public void setFrames(List<BufferedImage> frames) {
        this.frames = frames;
        this.selected = Math.clamp(frames.size() - 1, 0, this.selected);
        this.repaint();
    }

    public void setSelected(int selected) {
        this.selected = selected;
        this.repaint();
    }

    public void setBackdrop(int rgb) {
        this.backdrop = rgb;
        this.repaint();
    }

    /** Frames identical to their predecessor, so an over-fine phase count is visible rather than logged. */
    public void setDuplicates(List<Integer> duplicates) {
        this.duplicates = Set.copyOf(duplicates);
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setColor(new Color(this.backdrop));
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
        if (this.frames.isEmpty()) {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        BufferedImage large = this.blend(this.frames.get(Math.min(this.selected, this.frames.size() - 1)));
        int size = large.getWidth() * LARGE_SCALE;
        int x = (this.getWidth() - size) / 2;
        g.drawImage(large, x, GAP, size, size, null);

        int stripY = GAP + size + GAP;
        int cell = this.frames.getFirst().getWidth() * STRIP_SCALE;
        int perRow = Math.max(1, (this.getWidth() - GAP * 2) / cell);

        for (int frame = 0; frame < this.frames.size(); frame++) {
            int column = frame % perRow;
            int row = frame / perRow;
            int cellX = GAP + column * cell;
            int cellY = stripY + row * cell;
            g.drawImage(this.blend(this.frames.get(frame)), cellX, cellY, cell, cell, null);

            if (frame == this.selected) {
                g.setColor(Color.WHITE);
                g.drawRect(cellX, cellY, cell - 1, cell - 1);
            } else if (this.duplicates.contains(frame)) {
                g.setColor(Color.ORANGE);
                g.drawRect(cellX, cellY, cell - 1, cell - 1);
            }
        }
    }

    /** Adds the sprite onto the backdrop, matching {@code BlendFunction.OVERLAY}'s src-alpha-plus-one. */
    private BufferedImage blend(BufferedImage sprite) {
        BufferedImage out = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_RGB);
        int backdropRed = this.backdrop >> 16 & 0xFF;
        int backdropGreen = this.backdrop >> 8 & 0xFF;
        int backdropBlue = this.backdrop & 0xFF;

        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int argb = sprite.getRGB(x, y);
                float alpha = (argb >>> 24) / 255.0F;
                int red = Math.min(255, backdropRed + Math.round((argb >> 16 & 0xFF) * alpha));
                int green = Math.min(255, backdropGreen + Math.round((argb >> 8 & 0xFF) * alpha));
                int blue = Math.min(255, backdropBlue + Math.round((argb & 0xFF) * alpha));
                out.setRGB(x, y, red << 16 | green << 8 | blue);
            }
        }

        return out;
    }

}
