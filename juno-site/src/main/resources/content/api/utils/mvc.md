---
title: "MVC"
description: "Organizing a multi-screen Juno app with a model, views, a navigator and a router, using the util.mvc package."
layout: page
---

A program with more than one screen, such as a menu, a list, a detail page and a settings page, is easiest to keep
correct when each part has one job. Juno's [`util.mvc`](https://github.com/jabrena/juno/tree/main/juno-api/src/main/java/io/github/jabrena/juno/util/mvc)
package provides the pieces for the model-view-controller split: the views, the way they hand over control, and the
loop that runs them.

| Part | Juno type | Responsibility |
|---|---|---|
| Model | your own class, such as `MailboxModel` | the program's state, in fixed-size arrays; no screen logic |
| View | [`View`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/mvc/View.java) or `KeyboardView` | draws one screen from the model and turns touches or keys into actions |
| Navigator | [`Navigator`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/mvc/Navigator.java) | `show(viewId)` and `back()`, the only way a view changes the screen |
| Router | [`ViewRouter`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/mvc/ViewRouter.java) or `KeyboardViewRouter` | owns the views, runs the frame loop and applies navigation between frames |
| Background task | [`Task`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/mvc/Task.java) | work advanced one short step per frame, such as HTTP requests, without a thread |

Views never refer to each other. A view knows only the integer id each screen was registered under, so a screen can be
moved, renamed or removed without touching the rest.

## Layout of a Juno app

The mailbox client in `juno-examples` shows the layout most apps can follow. Each concern gets its own file, and the
main class only wires them together:

```text
usecases/mailbox/client/
├── MailboxCardputer.java    main: builds the objects and the router, then runs the loop
├── MailboxModel.java        state shared by the views and the client
├── MailboxClient.java       a Task: the HTTPS requests, filling the model
├── MailboxConnection.java   Wi-Fi join, advanced by the client each frame
├── MailboxKey.java          settings read from the SD card
├── CoverView.java           one screen per file, each implementing View or KeyboardView
├── SettingsView.java
├── InboxView.java
├── MessageView.java
├── ComposeView.java
└── ProgressView.java
```

Put the code that talks to the hardware or the network in a service or a `Task`, the state in the model, and the drawing
and input in the views. The main class has no screen logic, so reading it shows which screens exist and how they are
connected.

## The model

The model holds what the views display. It has no reference to a view and no drawing code. Juno needs every array
length at compile time, so the model sizes its buffers with constants:

```java
public final class MailboxModel {
    public static final int MAX_MESSAGES = 4;
    public static final int MAX_TITLE_BYTES = 64;

    private final byte[][] titles = new byte[MAX_MESSAGES][MAX_TITLE_BYTES];
    private final int[] titleLengths = new int[MAX_MESSAGES];
    private int messageCount;
    private int revision;
    private String status = "Waiting for mailbox";

    /** A changed value bumps the revision, so a view knows it must redraw. */
    public void status(String message) {
        if (!message.equals(status)) {
            status = message;
            revision = revision + 1;
        }
    }

    public int revision() {
        return revision;
    }
}
```

The `revision` counter is a cheap way for a view to know that something changed. The view compares it with the value it
last drew, and redraws only when they differ.

## The views

A view implements `View`: `enter()` runs once when the screen becomes active, `render()` runs every frame, and `touch(x, y)`
runs for each new touch. A `KeyboardView` replaces `touch` with `key(key, fnPressed)`. To change screen, the view calls
`navigator.show(id)`; to go back, `navigator.back()`:

```java
public final class InboxView implements View {
    private static final int MESSAGE_VIEW = 2;

    private final Navigator navigator;
    private final MailboxModel model;
    private int drawnRevision = -1;

    public InboxView(Navigator navigator, MailboxModel model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        drawnRevision = -1;
        // Clear the display and draw what never changes, such as the title bar.
    }

    @Override
    public void render() {
        if (model.revision() != drawnRevision) {
            drawnRevision = model.revision();
            // Redraw only the rows that changed.
        }
    }

    @Override
    public void touch(int x, int y) {
        // Map the touch to a message index, then move to its detail view.
        navigator.show(MESSAGE_VIEW);
    }
}
```

Two rules keep views simple. A view draws only what changed, because a full redraw each frame is slow on a small display.
And a view never calls another view's methods: it asks the navigator and reads the model.

## Wiring it together

The main class builds every object once at startup, registers each view under a fixed id, and starts the loop. This
sketch trims the mailbox example to its essentials. The mailbox client is registered as the background task, so its
requests run one step per frame:

```java
MailboxModel model = new MailboxModel();
MailboxClient client = new MailboxClient(model, connection, key, polling);

ViewRouter router = new ViewRouter(FRAME_MILLIS);
router.register(COVER_VIEW, new CoverView(router, display));
router.register(INBOX_VIEW, new InboxView(router, model));
router.register(MESSAGE_VIEW, new MessageView(router, model));
router.background(client);
router.start(COVER_VIEW);
router.run();          // never returns
```

Each view takes the `Navigator` it needs (the router) and the model, nothing else. Passing the views the router rather than
each other is what keeps the graph of references flat.

`router.run()` repeats the same steps every frame:

1. read the touch panel and give a new touch to the active view;
2. advance the background task one step;
3. apply a pending `show` or `back`, calling the new view's `enter()`;
4. let the active view `render()`;
5. wait until the next frame is due.

A navigation request takes effect between frames, never in the middle of a touch handler, so a view can ask to change
screen from its own `touch` method safely.

## Keyboard boards

Boards with a keyboard, such as the M5Stack Cardputer, use `KeyboardViewRouter` with `KeyboardView` screens. It takes the
`Keyboard` in place of the touch panel and calls `key(key, fnPressed)` for each key pressed in a transition. The rest
of the layout is unchanged; [`ZeldaOcarinaCardputer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/zelda/ZeldaOcarinaCardputer.java)
follows this pattern.

## Limits

- A router holds at most `ViewRouter.MAX_VIEWS` (8) views, numbered from `0`.
- The back stack remembers four screens; the oldest is dropped when it is full. `back()` does nothing when the stack
  is empty.
- The frame loop allocates nothing, so every view, model and buffer is created before `run()`.
- Runtime `String` values are short-lived. Do not keep one, such as a Wi-Fi password read from the SD card, in a field
  that outlives the call that loaded it; copy what you need into a fixed buffer.

## Testing

Pass a mocked clock to the router, and mock the `Navigator` for a view, so each view can be unit-tested without a board:

```java
Navigator navigator = mock(Navigator.class);
InboxView view = new InboxView(navigator, model);
view.touch(10, 40);
verify(navigator).show(MESSAGE_VIEW);
```

`ViewRouter(int, Clock)` takes the clock as a parameter for the same reason.

## Troubleshooting

- **A screen never changes:** check that the id passed to `show` is the same constant the view was registered under.
- **Taps are missed:** the router reads the touch panel once per frame, so a tap shorter than one frame can be missed,
  and a slow `render` delays every touch. Keep `render` short, and move slow work into a `Task`.
- **The display flickers:** `render` redraws everything every frame. Compare the model's revision first, as in the
  inbox example above.
- **The build runs out of arena memory:** the startup objects of a larger app can outgrow the default arena. Build with a
  larger `-Djuno.Xmx`, as the mailbox client does; see [Memory model](../memory-model).
