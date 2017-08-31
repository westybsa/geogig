/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.VERSION;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.SynchronizedGraphDatabase;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.inject.Inject;

public class RocksdbObjectDatabase extends RocksdbObjectStore implements ObjectDatabase {

    private RocksdbBlobStore blobs;

    private final ConfigDatabase configdb;

    private RocksdbGraphDatabase graph;

    @Inject
    public RocksdbObjectDatabase(Platform platform, Hints hints, ConfigDatabase configdb) {
        super(platform, hints);
        this.configdb = configdb;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.OBJECT.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean isReadOnly() {
        return super.readOnly;
    }

    @Override
    public RocksdbBlobStore getBlobStore() {
        return blobs;
    }

    @Override
    public GraphDatabase getGraphDatabase() {
        return new SynchronizedGraphDatabase(graph);
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        super.open();
        try {
            File blobsDir = new File(super.path, "blobs");
            blobsDir.mkdir();
            this.blobs = new RocksdbBlobStore(blobsDir, super.readOnly);
            this.graph = new RocksdbGraphDatabase(platform, hints);
            this.graph.open();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        try {
            super.close();
        } finally {
            RocksdbBlobStore blobs = this.blobs;
            RocksdbGraphDatabase graph = this.graph;
            this.blobs = null;
            this.graph = null;
            try {
                Closeables.close(blobs, true);
                Closeables.close(graph, true);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Overrides to add graphdb mapping on commits
     */
    public @Override boolean put(final RevObject object) {
        final boolean added = super.put(object);
        if (added && TYPE.COMMIT.equals(object.getType())) {
            RevCommit c = (RevCommit) object;
            graph.put(c.getId(), c.getParentIds());
        }
        return added;
    }

    protected @Override void putAll(Stream<RevObject> stream, BulkOpListener listener) {

        final int commitsBatchSize = 10_000;
        Set<RevCommit> insertedCommits = Sets.newHashSet();

        Consumer<RevObject> updateCommits = (o) -> {
            if (TYPE.COMMIT.equals(o.getType())) {
                synchronized (insertedCommits) {
                    insertedCommits.add((RevCommit) o);
                    if (insertedCommits.size() >= commitsBatchSize) {
                        graph.putAll(insertedCommits);
                        insertedCommits.clear();
                    }
                }
            }
        };
        // the stream will call updateCommits for each object as they're consumed
        stream = stream.peek(updateCommits);
        super.putAll(stream, listener);
        if (!insertedCommits.isEmpty()) {
            graph.putAll(insertedCommits);
        }
    }
}
