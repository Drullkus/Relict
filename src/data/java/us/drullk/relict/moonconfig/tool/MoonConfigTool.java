package us.drullk.relict.moonconfig.tool;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.moonconfig.GlbModel;
import us.drullk.relict.moonconfig.MoonRasterizer;
import us.drullk.relict.moonconfig.MoonSpriteConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Visual tuning for the moon sprite configs.
 * <p>
 * The tool owns no rendering code of its own: it edits parameters, calls {@link MoonRasterizer}, and blits
 * the result. That is what keeps it honest — anything it can show, datagen can reproduce byte for byte,
 * because it is the same pipeline. Saving writes the config through its codec, so the tool cannot even
 * express a file datagen would reject.
 */
public final class MoonConfigTool {

    /**
     * Sky colours to judge the sprites against, mirroring the Mars dimension type and its sol timeline.
     * Additive blending means the backdrop decides whether a glow reads at all, so previewing on one is the
     * only way to tune it. Preview-only: update these if the dimension's palette moves.
     */
    private static final Map<String, Integer> BACKDROPS = new LinkedHashMap<>();

    static {
        BACKDROPS.put("Midnight", 0x000000);
        BACKDROPS.put("Mars night", 0x0B0A12);
        BACKDROPS.put("Mars dusk", 0x4A2A1E);
        BACKDROPS.put("Mars noon", 0xD8A07A);
    }

    private static final int RENDER_DEBOUNCE_MS = 60;

    private final Path moonsDir;
    private final Map<String, GlbModel> models = new HashMap<>();
    private final SpritePreview preview = new SpritePreview();
    private final JLabel status = new JLabel(" ");
    private final JPanel controls = new JPanel();
    private final Timer debounce;

    private String moon;
    private MoonSpriteConfig config;
    private int frames;
    private RampEditor gradientEditor;
    private RampEditor haloEditor;
    private JTextField paletteField;

    private MoonConfigTool(Path moonsDir) {
        this.moonsDir = moonsDir;
        this.debounce = new Timer(RENDER_DEBOUNCE_MS, _ -> this.render());
        this.debounce.setRepeats(false);
    }

    static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MoonConfigTool <path to src/data/resources/moons>");
            System.exit(2);
        }

        Path moonsDir = Path.of(args[0]);
        SwingUtilities.invokeLater(() -> new MoonConfigTool(moonsDir).show());
    }

    private void show() {
        List<String> moons = this.discoverMoons();
        if (moons.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No configs found in " + this.moonsDir.resolve("gen-configs"));
            return;
        }

        JFrame frame = new JFrame("Relict moon config");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JComboBox<String> moonPicker = new JComboBox<>(moons.toArray(String[]::new));
        moonPicker.addActionListener(_ -> this.load((String) moonPicker.getSelectedItem()));

        JComboBox<String> backdropPicker = new JComboBox<>(BACKDROPS.keySet().toArray(String[]::new));
        backdropPicker.addActionListener(_ -> this.preview.setBackdrop(BACKDROPS.get((String) backdropPicker.getSelectedItem())));

        JButton save = new JButton("Save config");
        save.addActionListener(_ -> this.save());
        JButton reload = new JButton("Revert");
        reload.addActionListener(_ -> this.load(this.moon));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Moon"));
        top.add(moonPicker);
        top.add(new JLabel("Backdrop"));
        top.add(backdropPicker);
        top.add(save);
        top.add(reload);

        this.controls.setLayout(new BoxLayout(this.controls, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(this.controls);
        scroll.setPreferredSize(new Dimension(400, 700));

        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.WEST);
        frame.add(this.preview, BorderLayout.CENTER);
        frame.add(this.status, BorderLayout.SOUTH);

        this.load(moons.getFirst());
        frame.pack();
        frame.setVisible(true);
    }

    private List<String> discoverMoons() {
        Path configs = this.moonsDir.resolve("gen-configs");
        if (!Files.isDirectory(configs)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(configs)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void load(String moon) {
        this.moon = moon;
        this.frames = framesFor(moon);
        this.config = readConfig(moon);
        this.rebuildControls();
        this.render();
    }

    private MoonSpriteConfig readConfig(String moon) {
        Path path = this.moonsDir.resolve("gen-configs").resolve(moon + ".json");

        try {
            JsonElement json = JsonParser.parseString(Files.readString(path));
            return MoonSpriteConfig.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(message -> new IllegalStateException(path + ": " + message));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    private void save() {
        Path path = this.moonsDir.resolve("gen-configs").resolve(this.moon + ".json");

        try {
            JsonElement json = MoonSpriteConfig.CODEC.encodeStart(JsonOps.INSTANCE, this.config)
                    .getOrThrow(message -> new IllegalStateException("Could not encode config: " + message));
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
            this.status.setText("Saved " + path + " — run runClientData to regenerate the sprites");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }

    private GlbModel model() {
        return this.models.computeIfAbsent(this.config.model(), resource -> {
            try {
                return GlbModel.read(Files.readAllBytes(this.moonsDir.resolveSibling(resource)));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + resource, e);
            }
        });
    }

    /** Re-renders every frame. Cheap enough to do on each edit, so there is no separate preview quality. */
    private void render() {
        MoonRasterizer.Result result = MoonRasterizer.render(this.model(), this.config, this.frames);
        this.preview.setFrames(result.frames());
        this.preview.setDuplicates(result.duplicates());
        this.status.setText("%s: %d frames, peak %.3f%s%s".formatted(
                this.moon,
                result.frames().size(),
                result.peakValue(),
                result.levelsMeasured() ? ", levels measured %.3f..%.3f".formatted(result.levels().min(), result.levels().max()) : "",
                result.duplicates().isEmpty() ? "" : ", duplicate frames " + result.duplicates()));
    }

    private void update(MoonSpriteConfig config) {
        this.config = config;
        this.debounce.restart();
    }

    private void rebuildControls() {
        this.controls.removeAll();

        MoonSpriteConfig.Axis[] axes = MoonSpriteConfig.Axis.values();
        this.controls.add(section("Orientation"));
        this.controls.add(row("View axis", combo(axes, this.config.orientation().view(), value -> this.update(
                withOrientation(this.config, new MoonSpriteConfig.Orientation(value, this.config.orientation().up(),
                        this.config.orientation().yaw(), this.config.orientation().pitch(), this.config.orientation().roll()))))));
        this.controls.add(row("Up axis", combo(axes, this.config.orientation().up(), value -> this.update(
                withOrientation(this.config, new MoonSpriteConfig.Orientation(this.config.orientation().view(), value,
                        this.config.orientation().yaw(), this.config.orientation().pitch(), this.config.orientation().roll()))))));
        this.controls.add(row("Yaw", degrees(this.config.orientation().yaw(), value -> this.update(
                withOrientation(this.config, new MoonSpriteConfig.Orientation(this.config.orientation().view(), this.config.orientation().up(),
                        value, this.config.orientation().pitch(), this.config.orientation().roll()))))));
        this.controls.add(row("Pitch", degrees(this.config.orientation().pitch(), value -> this.update(
                withOrientation(this.config, new MoonSpriteConfig.Orientation(this.config.orientation().view(), this.config.orientation().up(),
                        this.config.orientation().yaw(), value, this.config.orientation().roll()))))));
        this.controls.add(row("Roll", degrees(this.config.orientation().roll(), value -> this.update(
                withOrientation(this.config, new MoonSpriteConfig.Orientation(this.config.orientation().view(), this.config.orientation().up(),
                        this.config.orientation().yaw(), this.config.orientation().pitch(), value))))));

        this.controls.add(section("Lighting"));
        this.controls.add(row("Terminator wrap", slider(this.config.lighting().wrap(), 0.0F, 1.0F, value -> this.update(
                withLighting(this.config, new MoonSpriteConfig.Lighting(value, this.config.lighting().phaseOffset(),
                        this.config.lighting().phaseAxis(), this.config.lighting().gain()))))));
        this.controls.add(row("Gain", slider(this.config.lighting().gain(), 0.1F, 4.0F, value -> this.update(
                withLighting(this.config, new MoonSpriteConfig.Lighting(this.config.lighting().wrap(), this.config.lighting().phaseOffset(),
                        this.config.lighting().phaseAxis(), value))))));
        this.controls.add(row("Phase offset", degrees(this.config.lighting().phaseOffset(), value -> this.update(
                withLighting(this.config, new MoonSpriteConfig.Lighting(this.config.lighting().wrap(), value,
                        this.config.lighting().phaseAxis(), this.config.lighting().gain()))))));
        JSpinner phaseAxis = degrees(this.config.lighting().phaseAxis(), value -> this.update(
                withLighting(this.config, new MoonSpriteConfig.Lighting(this.config.lighting().wrap(), this.config.lighting().phaseOffset(),
                        value, this.config.lighting().gain()))));
        phaseAxis.setToolTipText("Which way the terminator sweeps, counterclockwise from sprite-right. "
                + "90 is a horizontal terminator, which is what the sky wants; 180 swaps waxing for waning.");
        this.controls.add(row("Phase axis", phaseAxis));

        this.controls.add(section("Rim light (silhouette outline)"));
        this.controls.add(row("Strength", slider(this.config.rimLight().strength(), 0.0F, 0.6F, value -> this.update(
                withRim(this.config, new MoonSpriteConfig.RimLight(value))))));

        this.controls.add(section("Value"));
        this.controls.add(row("Posterize steps", steps(this.config.posterize(), value -> this.update(withPosterize(this.config, value)))));

        this.controls.add(section("Gradient map"));
        this.gradientEditor = new RampEditor(this.config.gradient(), () -> this.update(withGradient(this.config, this.gradientEditor.ramp())));
        this.controls.add(this.gradientEditor);
        this.controls.add(row("Interpolation", combo(MoonSpriteConfig.Interpolation.values(), this.config.gradient().interpolation(),
                value -> this.gradientEditor.setInterpolation(value))));

        this.controls.add(section("Palette snap"));
        this.paletteField = new JTextField(paletteText(this.config.palette()));
        this.paletteField.setToolTipText("Comma-separated #rrggbb, or empty for no snapping. Paste a palette straight in.");
        this.paletteField.addActionListener(_ -> this.commitPalette());
        this.paletteField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                MoonConfigTool.this.commitPalette();
            }
        });
        this.controls.add(row("Colours", this.paletteField));

        this.controls.add(section("Halo"));
        this.controls.add(row("Inner radius", slider(this.config.halo().innerRadius(), 0.0F, 1.5F, value -> this.update(
                withHalo(this.config, new MoonSpriteConfig.Halo(value, this.config.halo().outerRadius(), this.config.halo().ramp()))))));
        this.controls.add(row("Outer radius", slider(this.config.halo().outerRadius(), 0.05F, 2.0F, value -> this.update(
                withHalo(this.config, new MoonSpriteConfig.Halo(this.config.halo().innerRadius(), value, this.config.halo().ramp()))))));
        this.haloEditor = new RampEditor(this.config.halo().ramp(), () -> this.update(withHalo(this.config,
                new MoonSpriteConfig.Halo(this.config.halo().innerRadius(), this.config.halo().outerRadius(), this.haloEditor.ramp()))));
        this.controls.add(this.haloEditor);

        this.controls.add(section("Preview"));
        JSlider phaseSlider = new JSlider(0, Math.max(1, this.frames - 1), 0);
        phaseSlider.addChangeListener(_ -> this.preview.setSelected(phaseSlider.getValue()));
        this.controls.add(row("Phase frame", phaseSlider));
        this.controls.add(Box.createVerticalGlue());

        this.controls.revalidate();
        this.controls.repaint();
    }

    // Records are immutable, so each control rebuilds the config around its own field.

    private static MoonSpriteConfig withOrientation(MoonSpriteConfig config, MoonSpriteConfig.Orientation orientation) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                orientation, config.lighting(), config.rimLight(), config.levels(), config.posterize(), config.gradient(),
                config.palette(), config.halo());
    }

    private static MoonSpriteConfig withLighting(MoonSpriteConfig config, MoonSpriteConfig.Lighting lighting) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), lighting, config.rimLight(), config.levels(), config.posterize(), config.gradient(),
                config.palette(), config.halo());
    }

    private static MoonSpriteConfig withRim(MoonSpriteConfig config, MoonSpriteConfig.RimLight rimLight) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), config.lighting(), rimLight, config.levels(), config.posterize(), config.gradient(),
                config.palette(), config.halo());
    }

    private static MoonSpriteConfig withPosterize(MoonSpriteConfig config, Optional<Integer> posterize) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), config.lighting(), config.rimLight(), config.levels(), posterize, config.gradient(),
                config.palette(), config.halo());
    }

    private static MoonSpriteConfig withGradient(MoonSpriteConfig config, MoonSpriteConfig.Ramp gradient) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), config.lighting(), config.rimLight(), config.levels(), config.posterize(), gradient,
                config.palette(), config.halo());
    }

    private static MoonSpriteConfig withPalette(MoonSpriteConfig config, Optional<List<Integer>> palette) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), config.lighting(), config.rimLight(), config.levels(), config.posterize(), config.gradient(),
                palette, config.halo());
    }

    private static MoonSpriteConfig withHalo(MoonSpriteConfig config, MoonSpriteConfig.Halo halo) {
        return new MoonSpriteConfig(config.model(), config.spriteDir(), config.canvas(), config.body(), config.supersample(),
                config.orientation(), config.lighting(), config.rimLight(), config.levels(), config.posterize(), config.gradient(),
                config.palette(), halo);
    }

    private void commitPalette() {
        this.update(withPalette(this.config, parsePalette(this.paletteField.getText())));
    }

    private static String paletteText(Optional<List<Integer>> palette) {
        return palette.map(colours -> colours.stream().map("#%06x"::formatted).reduce((a, b) -> a + ", " + b).orElse(""))
                .orElse("");
    }

    private static Optional<List<Integer>> parsePalette(String text) {
        List<Integer> colours = new ArrayList<>();

        for (String token : text.split(",")) {
            String trimmed = token.trim().replace("#", "");
            if (!trimmed.isEmpty()) {
                colours.add(Integer.parseInt(trimmed, 16));
            }
        }

        return colours.isEmpty() ? Optional.empty() : Optional.of(colours);
    }

    private static JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(12, 8, 2, 8));
        label.setFont(label.getFont().deriveFont(label.getFont().getSize() + 1.0F).deriveFont(java.awt.Font.BOLD));
        return label;
    }

    private static JPanel row(String label, java.awt.Component control) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        JLabel name = new JLabel(label);
        name.setPreferredSize(new Dimension(120, 22));
        panel.add(name, BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private static <E> JComboBox<E> combo(E[] values, E selected, java.util.function.Consumer<E> onPick) {
        JComboBox<E> box = new JComboBox<>(values);
        box.setSelectedItem(selected);
        box.addActionListener(_ -> onPick.accept(box.getItemAt(box.getSelectedIndex())));
        return box;
    }

    /** Sliders are integral, so float parameters ride on a fixed 1000-step scale. */
    private static JSlider slider(float value, float min, float max, java.util.function.Consumer<Float> onChange) {
        JSlider slider = new JSlider(0, 1000, Math.round((value - min) / (max - min) * 1000.0F));
        slider.addChangeListener(_ -> onChange.accept(min + slider.getValue() / 1000.0F * (max - min)));
        return slider;
    }

    private static JSpinner degrees(float value, java.util.function.Consumer<Float> onChange) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, -360.0, 360.0, 1.0));
        spinner.addChangeListener(_ -> onChange.accept(((Number) spinner.getValue()).floatValue()));
        return spinner;
    }

    /** Zero means no posterizing, which the config expresses as an absent field. */
    private static JSpinner steps(Optional<Integer> value, java.util.function.Consumer<Optional<Integer>> onChange) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value.orElse(0).intValue(), 0, 64, 1));
        spinner.addChangeListener(_ -> {
            int steps = ((Number) spinner.getValue()).intValue();
            onChange.accept(steps < 2 ? Optional.empty() : Optional.of(steps));
        });
        return spinner;
    }

    /**
     * The one number the tool takes from the mod proper, so its filmstrip matches what datagen writes.
     * <p>
     * These are compile-time int constants, so javac inlines them here and no Minecraft class is ever
     * loaded — the tool stays a plain JVM program.
     */
    private static int framesFor(String moon) {
        return switch (moon) {
            case "phobos" -> RelictDimension.PHOBOS_PHASES;
            case "deimos" -> RelictDimension.DEIMOS_PHASES;
            default -> throw new IllegalArgumentException("RelictDimension has no phase count for moon " + moon);
        };
    }

}
