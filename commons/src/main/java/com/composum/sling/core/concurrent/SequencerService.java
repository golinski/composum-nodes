package com.composum.sling.core.concurrent;

import org.jetbrains.annotations.NotNull;

/**
 * A general service to sequence potentially concurrent modifications using a key (e.g. a resource path)
 * to ensure that only one thread makes modifications in the context of the key at a time.
 */
public interface SequencerService<T extends SequencerService.Token> {

    interface Token {}

    /**
     * Ensures the exclusiveness regarding the 'key' bind to the returned token. Remember to use this in a try finally
     * with {@link #release(Token)}.
     * @return the token which is necessary to release the binding
     * @throws IllegalStateException if we could not acquire the lock for one hour - there must be something broken.
     */
    @NotNull
    T acquire(@NotNull String key);

    /**
     * Stops the exclusive access to the 'key' encapsulated in the token which
     * was generated by the corresponding 'acquire()'. Must be called after a successful {@link #acquire(String)}, and
     * the Token must be discarded afterwards.
     */
    void release(@NotNull T token);
}
