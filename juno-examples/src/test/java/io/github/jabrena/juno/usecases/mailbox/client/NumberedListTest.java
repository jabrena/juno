package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NumberedListTest {
    private static final int TEXT_X = 4 + 3 * 12;

    @Test
    void prefixesEachTitleWithItsNumber() {
        // Given
        Display display = mock(Display.class);
        MailboxModel model = model("Hello", "World");
        NumberedList list = new NumberedList(display, model);

        // When
        list.select(0);

        // Then
        verify(display).print('1');
        verify(display).print('2');
    }

    @Test
    void wrapsALongTitleOntoTheNextLinesAtWordBreaks() {
        // Given
        Display display = mock(Display.class);
        MailboxModel model = model("Please review the mailbox client wrapping", "Short");
        NumberedList list = new NumberedList(display, model);

        // When
        list.select(0);

        // Then the first title takes three 18-pixel lines and the second starts after it
        assertThat(rowsAt(display, TEXT_X)).containsExactly(2, 20, 38, 59);
    }

    private static List<Integer> rowsAt(Display display, int x) {
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(display, atLeastOnce()).setCursor(xs.capture(), ys.capture());
        List<Integer> rows = new java.util.ArrayList<>();
        for (int index = 0; index < xs.getAllValues().size(); index++) {
            if (xs.getAllValues().get(index) == x) {
                rows.add(ys.getAllValues().get(index));
            }
        }
        return rows;
    }

    private static MailboxModel model(String... titles) {
        MailboxModel model = new MailboxModel();
        byte[] uuid = new byte[MailboxModel.UUID_LENGTH];
        for (String title : titles) {
            byte[] bytes = title.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, model.title(model.messageCount()), 0, bytes.length);
            model.add(uuid, uuid.length, bytes.length, 0);
        }
        return model;
    }
}
