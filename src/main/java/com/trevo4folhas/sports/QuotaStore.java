package com.trevo4folhas.sports;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared, durable reservation before every HTTP attempt; no guessed daily reset. */
@Component
public class QuotaStore {
  public record State(
      Instant windowEndsAt,
      int used,
      int providerRemaining,
      Instant notBefore,
      Instant lastAttemptAt,
      Instant lastSuccessAt,
      String outcome) {}

  public record RunLock(FileChannel channel, FileLock lock) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      try {
        lock.release();
      } finally {
        channel.close();
      }
    }
  }

  private final SportsProperties properties;
  private final ObjectMapper mapper;
  private final Clock clock;

  public QuotaStore(SportsProperties properties, ObjectMapper mapper, Clock clock) {
    this.properties = properties;
    this.mapper = mapper;
    this.clock = clock;
  }

  private Path path() throws IOException {
    if (properties.quotaStatePath() == null) throw new IOException();
    Path path = Path.of(properties.quotaStatePath()).toAbsolutePath().normalize();
    Path real = path.toRealPath();
    if (!path.equals(real)
        || real.startsWith(Path.of(System.getProperty("user.dir")).toRealPath())
        || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)
        || !Files.getPosixFilePermissions(real).equals(PosixFilePermissions.fromString("rw-------"))
        || !Files.getPosixFilePermissions(real.getParent())
            .equals(PosixFilePermissions.fromString("rwx------"))) throw new IOException();
    return real;
  }

  public RunLock acquireRun() {
    try {
      Path lockPath = path().resolveSibling(path().getFileName() + ".run.lock");
      FileChannel channel =
          FileChannel.open(
              lockPath,
              java.util.Set.of(
                  StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      try {
        FileLock lock = channel.tryLock();
        if (lock == null) {
          channel.close();
          throw new SyncFailure("RUN_BUSY");
        }
        return new RunLock(channel, lock);
      } catch (OverlappingFileLockException busy) {
        channel.close();
        throw new SyncFailure("RUN_BUSY");
      }
    } catch (IOException | IllegalArgumentException failure) {
      throw new SyncFailure("QUOTA_STATE_UNAVAILABLE");
    }
  }

  public synchronized State read() {
    return modify(null);
  }

  public synchronized void reserve() {
    modify(
        s -> {
          Instant now = clock.instant();
          if (!s.windowEndsAt().isAfter(now)) throw new SyncFailure("QUOTA_RESET_UNVERIFIED");
          if (s.notBefore() != null && now.isBefore(s.notBefore()))
            throw new SyncFailure("RATE_LIMIT_WAIT");
          if (s.used() >= properties.dailyRequestBudget() - properties.requestReserve()
              || s.providerRemaining() <= properties.requestReserve())
            throw new SyncFailure("QUOTA_EXHAUSTED");
          return new State(
              s.windowEndsAt(),
              s.used() + 1,
              s.providerRemaining() - 1,
              s.notBefore(),
              now,
              s.lastSuccessAt(),
              "RUNNING");
        });
  }

  public synchronized void observe(int remaining, int minuteRemaining, Instant retryAt) {
    modify(
        s ->
            new State(
                s.windowEndsAt(),
                s.used(),
                remaining < 0 ? s.providerRemaining() : Math.min(s.providerRemaining(), remaining),
                later(
                    s.notBefore(),
                    retryAt != null
                        ? retryAt
                        : minuteRemaining == 0 ? clock.instant().plusSeconds(60) : null),
                s.lastAttemptAt(),
                s.lastSuccessAt(),
                s.outcome()));
  }

  public synchronized void finish(String outcome, boolean success) {
    modify(
        s ->
            new State(
                s.windowEndsAt(),
                s.used(),
                s.providerRemaining(),
                s.notBefore(),
                s.lastAttemptAt(),
                success ? clock.instant() : s.lastSuccessAt(),
                outcome));
  }

  private Instant later(Instant a, Instant b) {
    return a == null ? b : b == null || a.isAfter(b) ? a : b;
  }

  private State modify(UnaryOperator<State> change) {
    try {
      Path file = path();
      Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
      try (FileChannel lockChannel =
              FileChannel.open(
                  lockPath,
                  java.util.Set.of(
                      StandardOpenOption.CREATE,
                      StandardOpenOption.WRITE,
                      LinkOption.NOFOLLOW_LINKS),
                  PosixFilePermissions.asFileAttribute(
                      PosixFilePermissions.fromString("rw-------")));
          FileLock ignored = lockChannel.lock()) {
        if (Files.size(file) > 8192) throw new IOException();
        State state = mapper.readValue(Files.readAllBytes(file), State.class);
        if (state.windowEndsAt() == null
            || state.used() < 0
            || state.used() > 100
            || state.providerRemaining() < 0
            || state.windowEndsAt().isAfter(clock.instant().plusSeconds(86400)))
          throw new IOException();
        if (change == null) return state;
        State updated = change.apply(state);
        Path temp =
            Files.createTempFile(
                file.getParent(),
                "quota-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
          try (FileChannel output = FileChannel.open(temp, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(mapper.writeValueAsBytes(updated));
            while (bytes.hasRemaining()) output.write(bytes);
            output.force(true);
          }
          Files.move(
              temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
          try (FileChannel directory =
              FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
            directory.force(true);
          }
        } finally {
          Files.deleteIfExists(temp);
        }
        return updated;
      }
    } catch (IOException | IllegalArgumentException | OverlappingFileLockException failure) {
      throw new SyncFailure("QUOTA_STATE_UNAVAILABLE");
    }
  }
}
