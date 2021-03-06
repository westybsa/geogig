/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.locationtech.geogig.rest.repository.RepositoryProvider.BASE_REPOSITORY_ROUTE;
import static org.locationtech.geogig.rest.repository.RepositoryProvider.GEOGIG_ROUTE_PREFIX;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import static org.springframework.web.bind.annotation.RequestMethod.TRACE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.Parents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

/**
 *
 */
@RestController
@RequestMapping(path = GEOGIG_ROUTE_PREFIX + "/" + BASE_REPOSITORY_ROUTE
        + "/{repoName}/repo/getparents")
public class ParentsController extends AbstractRepositoryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParentsController.class);

    @RequestMapping(method = {PUT, POST, DELETE, PATCH, TRACE, OPTIONS})
    public void catchAll() {
        // if we hit this controller, it's a 405
        supportedMethods(Sets.newHashSet(GET.toString()));
    }

    @GetMapping
    public void getParents(@PathVariable(name = "repoName") String repoName,
            @RequestParam(name = "commitId", required = false) String commitId,
            HttpServletRequest request, HttpServletResponse response) {
        // get the provider
        Optional<RepositoryProvider> optional = getRepoProvider(request);
        if (optional.isPresent()) {
            final RepositoryProvider provider = optional.get();
            // ensure the repo exists and is opened
            if (!isOpenRepository(provider, repoName, response)) {
                // done
                return;
            }
            ObjectId oid = null;
            // extract the commitID if present
            if (commitId != null && (oid = getValidCommitId(commitId, response, false)) == null) {
                // commitID provided, but not valid
                // response was already handled
                return;
            }
            // get the affected features
            Parents parents =  repositoryService.getParents(provider, repoName, oid);
            // encode the affected features
            encode(parents, request, response);
        } else {
            throw NO_PROVIDER;
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}

