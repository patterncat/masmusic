package uk.ac.bath.masmusic.mas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import com.google.common.base.Objects;

import jason.JasonException;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSemantics.Circumstance;
import jason.asSemantics.TransitionSystem;
import jason.asSyntax.Literal;
import jason.asSyntax.directives.Include;
import jason.runtime.Settings;
import uk.ac.bath.masmusic.common.Rhythm;
import uk.ac.bath.masmusic.common.Scale;
import uk.ac.bath.masmusic.events.RhythmUpdatedEvent;
import uk.ac.bath.masmusic.events.ScaleUpdatedEvent;

/**
 * Base class for MasMusic agents.
 *
 * @author Javier Dehesa
 */
public abstract class MasMusicAbstractAgent extends AgArch {

    /** Logger */
    private static Logger LOG = LoggerFactory.getLogger(MasMusicAbstractAgent.class);

    /** Default note velocity. */
    public static final int DEFAULT_VELOCITY = 64;

    /** Default note duration. */
    public static final int DEFAULT_DURATION = 500;

    /** Event for heard notes. */
    public static final String HEAR_EVENT = "hear";

    /** Event for performance instructions. */
    public static final String PERFORM_EVENT = "perform";

    /** Event for beat. */
    public static final String BEAT_EVENT = "beat";

    /** Event for scale. */
    public static final String SCALE_EVENT = "scale";

    /** Event for rhythm. */
    private static final String RHYTHM_EVENT = "rhythm";

    /** MasMusic multi-agent system. */
    @Autowired
    private MasMusic masMusic;

    /** Agent hearing literals queue. */
    private final Queue<Literal> heard;

    /** Agent instructions literals queue. */
    private final List<Literal> instructions;

    /** Currently perceived rhythm literal. */
    private Literal currentRhythm;

    /** Currently perceived scale literal. */
    private Literal currentScale;

    /** Perceived literals. */
    private final List<Literal> percepts;

    public MasMusicAbstractAgent() {
        heard = new ConcurrentLinkedQueue<Literal>();
        instructions = Collections.synchronizedList(new ArrayList<>());
        currentRhythm = null;
        currentScale = null;
        percepts = new ArrayList<>();
    }

    /**
     * Initialize an agent with the given ASL source.
     *
     * @param aslPath
     *            ASL source resource path
     */
    protected void initAgent(String aslPath) {
        String agentSourcePath = Include.CRPrefix + aslPath;
        try {
            Agent ag = new Agent();
            new TransitionSystem(ag, new Circumstance(), new Settings(), this);
            ag.initAg(agentSourcePath);
        } catch (JasonException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Literal> perceive() {
        this.percepts.clear();
        List<Literal> percepts = Objects.firstNonNull(super.perceive(), this.percepts);
        if (currentRhythm != null) {
            percepts.add(currentRhythm);
        }
        if (currentScale != null) {
            percepts.add(currentScale);
        }
        if (!heard.isEmpty()) {
            percepts.add(heard.poll());
        }
        percepts.addAll(instructions);
        return percepts;
    }

    @Override
    public void act(ActionExec action, List<ActionExec> feedback) {
        super.act(action, feedback);
        if (doAction(action)) {
            action.setResult(true);
            feedback.add(action);
        }
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    /**
     * Carry out an action.
     *
     * @param action
     *            The action to perform
     * @return true if the action was carried out, false otherwise
     */
    protected abstract boolean doAction(ActionExec action);

    /**
     * Run a reasoning cycle of the agent.
     */
    public void reason() {
        if (isRunning()) {
            getTS().reasoningCycle();
        }
    }

    @Override
    public boolean canSleep() {
        return true;
    }

    @Override
    public void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Inform the agent about a note event.
     *
     * @param pitch
     *            Pitch of the note
     * @param velocity
     *            Velocity of the note
     * @param timestamp
     *            Timestamp at which the note was played
     */
    public void hear(int pitch, int velocity, long timestamp) {
        Literal literal = Literal.parseLiteral(
                String.format("%s(%d, %d)", HEAR_EVENT, pitch, velocity));
        heard.offer(literal);
    }

    /**
     * Instruct the agent to perform.
     *
     * @param start
     *            Timestamp of the beginning of the performance in milliseconds
     * @param bars
     *            Duration of the performance in bars
     */
    public void perform(long start, long bars) {
        Literal literal = Literal.parseLiteral(
                String.format("%s(%d, %d)", PERFORM_EVENT, start, bars));
        instructions.add(literal);
    }

    /**
     * Handle a rhythm update event.
     *
     * @param event
     *            The rhythm update event
     */
    @EventListener
    public void onRhythmUpdated(RhythmUpdatedEvent event) {
        Rhythm rhythm = event.getRhythm();
        int beatDuration = rhythm.getBeat().getDuration();
        int beatPhase = rhythm.getBeat().getPhase();
        int barBeats = rhythm.getTimeSignature().getBeats();
        int barUnit = rhythm.getTimeSignature().getUnit();
        int barBeatOffset = rhythm.getBeatOffset();
        currentRhythm = Literal.parseLiteral(
                String.format("%s(%d, %d, %d, %d, %d)", RHYTHM_EVENT,
                        beatDuration, beatPhase, barBeats, barUnit,
                        barBeatOffset));
    }

    /**
     * Handle a scale update event.
     *
     * @param event
     *            The scale update event
     */
    @EventListener
    public void onScaleUpdated(ScaleUpdatedEvent event) {
        Scale scale = event.getScale();
        currentScale = Literal.parseLiteral(String.format("%s(%d, %s)",
                SCALE_EVENT, scale.getFundamental().value(), scale.getType().toLowerCase()));
    }

    /**
     * @param pitch
     *            Pitch of the played note
     * @param velocity
     *            Velocity of the played note
     * @param timestamp
     *            Start time of the played note
     * @param duration
     *            Duration of the played note
     * @param instrument
     *            Instrument used to play the note
     */
    protected void playNote(int pitch, int velocity, long timestamp,
            int duration, int instrument) {
        masMusic.play(pitch, velocity, timestamp, duration, instrument);
    }
}
