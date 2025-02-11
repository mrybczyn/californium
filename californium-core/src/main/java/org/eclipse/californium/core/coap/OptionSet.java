/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and re-implementation
 *    Dominique Im Obersteg - parsers and initial implementation
 *    Daniel Pauli - parsers and initial implementation
 *    Kai Hudalla - logging
 *    Achim Kraus (Bosch Software Innovations GmbH) - make getOthers() public.
 *                                                    issue #286
 *    Achim Kraus (Bosch Software Innovations GmbH) - Include size1 and size2
 *                                                    in clone and clear
 *                                                    issue #815
 ******************************************************************************/
package org.eclipse.californium.core.coap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.californium.core.coap.OptionNumberRegistry.CustomOptionNumberRegistry;
import org.eclipse.californium.elements.util.Bytes;

/**
 * {@code OptionSet} is a collection of all options of a request or a response.
 * {@code OptionSet} provides methods to add, remove and modify all options
 * defined in the CoAP, blockwise CoAP, observing CoAP and supports arbitrary
 * defined options.
 * <p>
 * Native format of a {@code CoAP} options include its number and value. More
 * detailed format documentation of number and value format, see {@link Option}.
 * <p>
 * <b>NOTE:</b> {@code CoAP} defines {@code If-None-Match} option as empty, thus
 * using {@link Option} to inspect its {@code value} is meaningless. Either use
 * {@link Option} to check if this particular option exists or use method
 * {@link #hasIfNoneMatch()} in this class. Other option relationships between
 * {@code OptionSet} and {@link Option} may have little differences like
 * {@code Content-Format} and {@code Accept} whose methods
 * {@link #getContentFormat()} and {@link #getAccept()} will return
 * {@link MediaTypeRegistry#UNDEFINED} if option is not present. This generally
 * means that user may want to check if option actually exists before naively
 * trying to use these values.
 * <p>
 * Notice that this class is not entirely thread-safe: hasObserve =&gt; (int)
 * getObserve()
 * 
 * @see Option
 */
public final class OptionSet {

	private static final int MAX_OBSERVE_NO = (1 << 24) - 1;
	/*
	 * Options defined by the CoAP protocol
	 */
	private List<byte[]> if_match_list;
	private String       uri_host;
	private List<byte[]> etag_list;
	private boolean      if_none_match; // true if option is set
	private Integer      uri_port; // null if no port is explicitly defined
	private List<String> location_path_list;
	private List<String> uri_path_list;
	private Integer      content_format;
	private Long         max_age; // (0-4 bytes)
	private List<String> uri_query_list;
	private UriQueryParameter uri_query_parameter;
	private Integer      accept;
	private List<String> location_query_list;
	private String       proxy_uri;
	private String       proxy_scheme;
	private BlockOption  block1;
	private BlockOption  block2;
	private Integer      size1;
	private Integer      size2;
	private Integer      observe;
	private byte[]       oscore;
	private NoResponseOption no_response;

	// Arbitrary options
	private List<Option> others;

	/**
	 * Creates an empty set of options.
	 * <p>
	 * Once a list is touched and constructed it must never become null again.
	 * Non-lists can be null though.
	 */
	public OptionSet() {
		if_match_list       = null; // new LinkedList<byte[]>();
		uri_host            = null; // from sender
		etag_list           = null; // new LinkedList<byte[]>();
		if_none_match       = false;
		uri_port            = null; // from sender
		location_path_list  = null; // new LinkedList<String>();
		uri_path_list       = null; // new LinkedList<String>();
		content_format      = null;
		max_age             = null;
		uri_query_list      = null; // new LinkedList<String>();
		uri_query_parameter = null;
		accept              = null;
		location_query_list = null; // new LinkedList<String>();
		proxy_uri           = null;
		proxy_scheme        = null;
		block1              = null;
		block2              = null;
		size1               = null;
		size2               = null;
		observe             = null;
		oscore              = null;
		no_response         = null;

		others              = null; // new LinkedList<>();
	}

	/**
	 * Creates a deep copy of existing options.
	 * 
	 * @param origin the existing options to be copied
	 */
	public OptionSet(OptionSet origin) {
		if (origin == null) {
			throw new NullPointerException("option set must not be null!");
		}
		if_match_list       = copyList(origin.if_match_list);
		uri_host            = origin.uri_host;
		etag_list           = copyList(origin.etag_list);
		if_none_match       = origin.if_none_match;
		uri_port            = origin.uri_port;
		location_path_list  = copyList(origin.location_path_list);
		uri_path_list       = copyList(origin.uri_path_list);
		content_format      = origin.content_format;
		max_age             = origin.max_age;
		uri_query_list      = copyList(origin.uri_query_list);
		uri_query_parameter = origin.uri_query_parameter;
		accept              = origin.accept;
		location_query_list = copyList(origin.location_query_list);
		proxy_uri           = origin.proxy_uri;
		proxy_scheme        = origin.proxy_scheme;

		block1              = origin.block1;
		block2              = origin.block2;

		size1               = origin.size1;
		size2               = origin.size2;
		observe             = origin.observe;
		if(origin.oscore != null) {
			oscore          = origin.oscore.clone();
		}
		no_response         = origin.no_response;
		others              = copyList(origin.others);
	}

	/**
	 * Clears all options.
	 */
	public void clear() {
		if (if_match_list != null)
			if_match_list.clear();
		uri_host = null;
		if (etag_list != null)
			etag_list.clear();
		if_none_match = false;
		uri_port = null;
		if (location_path_list != null)
			location_path_list.clear();
		if (uri_path_list != null)
			uri_path_list.clear();
		content_format = null;
		max_age = null;
		if (uri_query_list != null) {
			uri_query_list.clear();
			uri_query_parameter = null;
		}
		accept = null;
		if (location_query_list != null)
			location_query_list.clear();
		proxy_uri = null;
		proxy_scheme = null;
		block1 = null;
		block2 = null;
		size1 = null;
		size2 = null;
		observe = null;
		oscore = null;
		no_response = null;
		if (others != null)
			others.clear();
	}

	/**
	 * Copies the specified list.
	 * 
	 * @param <T> the generic type
	 * @param list the list
	 * @return a copy of the list
	 */
	private <T> List<T> copyList(List<T> list) {
		if (list == null)
			return null;
		else
			return new LinkedList<T>(list);
	}

	/////////////////////// Getter and Setter ///////////////////////

	/**
	 * Gets the list of If-Match ETags.
	 * <p>
	 * The OptionSet uses lazy initialization for this list.
	 * 
	 * @return the list of If-Match ETags
	 */
	public List<byte[]> getIfMatch() {
		synchronized (this) {
			if (if_match_list == null)
				if_match_list = new LinkedList<byte[]>();
		}
		return if_match_list;
	}

	/**
	 * Gets the number of If-Match options.
	 * 
	 * @return the count
	 */
	public int getIfMatchCount() {
		return getIfMatch().size();
	}

	/**
	 * Checks if the If-Match options contain the given ETag.
	 * <p>
	 * This method can be used by a server to handle a conditional request. When
	 * called, the method assumes the resource does exist, so that an empty
	 * If-Match option will match. The passed ETag should be the one by the
	 * server denoting the current resource state.
	 * 
	 * @param check the ETag of the current resource state
	 * @return {@code true}, if ETag matches or message contains an empty If-Match option
	 */
	public boolean isIfMatch(byte[] check) {

		// if no If-Match option is present, conditional update is allowed
		if (if_match_list == null)
			return true;

		for (byte[] etag : if_match_list) {
			// an empty If-Match option checks for existence of the resource
			if (etag.length == 0)
				return true;
			if (Arrays.equals(etag, check))
				return true;
		}
		return false;
	}

	/**
	 * Adds an ETag to the If-Match options.
	 * <p>
	 * A byte array of size 0 adds an empty If-Match option, which checks for
	 * existence of the targeted resource.
	 * 
	 * @param etag the If-Match ETag to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the etag is {@code null}
	 * @throws IllegalArgumentException if the etag has more than 8 bytes.
	 */
	public OptionSet addIfMatch(byte[] etag) {
		checkOptionValue(OptionNumberRegistry.IF_MATCH, etag);
		getIfMatch().add(etag);
		return this;
	}

	/**
	 * Removes a specific ETag from the If-Match options.
	 * 
	 * @param etag the If-Match ETag to remove
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeIfMatch(byte[] etag) {
		getIfMatch().remove(etag);
		return this;
	}

	/**
	 * Removes all If-Match options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearIfMatchs() {
		getIfMatch().clear();
		return this;
	}

	/**
	 * Gets the string value of the Uri-Host option.
	 * 
	 * @return the Uri-Host, or {@code null}, if the option is not present
	 */
	public String getUriHost() {
		return uri_host;
	}

	/**
	 * Checks if the Uri-Host option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasUriHost() {
		return uri_host != null;
	}

	/**
	 * Sets the Uri-Host option.
	 * 
	 * @param host the Uri-Host value to set.
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the host is {@code null}
	 * @throws IllegalArgumentException if the host has less than 1 or more than
	 *             255 bytes.
	 */
	public OptionSet setUriHost(String host) {
		checkOptionValue(OptionNumberRegistry.URI_HOST, host);
		this.uri_host = host;
		return this;
	}

	/**
	 * Removes the Uri-Host option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeUriHost() {
		this.uri_host = null;
		return this;
	}

	/**
	 * Gets the list of ETags.
	 * <p>
	 * In a response, there MUST only be one ETag that defines the payload or
	 * the resource given through the Location-* options. In a request, there
	 * can be multiple ETags for validation. The OptionSet uses lazy
	 * initialization for this list.
	 * 
	 * @return the list of ETags
	 */
	public List<byte[]> getETags() {
		synchronized (this) {
			if (etag_list == null)
				etag_list = new LinkedList<byte[]>();
		}
		return etag_list;
	}

	/**
	 * Gets the number of ETag options.
	 * 
	 * @return the count
	 */
	public int getETagCount() {
		return getETags().size();
	}

	/**
	 * Checks if the ETag options contain the passed ETag.
	 * <p>
	 * This can be used by a server to respond to a validation request. The
	 * passed ETag should be the one by the server denoting the current resource
	 * state.
	 * 
	 * @param check the ETag of the current resource state
	 * @return {@code true}, if ETag is included
	 */
	public boolean containsETag(byte[] check) {
		if (etag_list == null)
			return false;
		for (byte[] etag : etag_list) {
			if (Arrays.equals(etag, check))
				return true;
		}
		return false;
	}

	/**
	 * Adds an ETag to the ETag options.
	 * 
	 * @param etag the ETag to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the etag is {@code null}
	 * @throws IllegalArgumentException if the etag has less than 1 or more than
	 *             8 bytes.
	 */
	public OptionSet addETag(byte[] etag) {
		checkOptionValue(OptionNumberRegistry.ETAG, etag);
		if (!containsETag(etag)) {
			getETags().add(etag.clone());
		}
		return this;
	}

	/**
	 * Removes a specific ETag from the ETag options.
	 * 
	 * @param etag the ETag to remove
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the etag is {@code null}
	 * @throws IllegalArgumentException if the etag has less than 1 or more than
	 *             8 bytes.
	 */
	public OptionSet removeETag(byte[] etag) {
		checkOptionValue(OptionNumberRegistry.ETAG, etag);
		if (etag_list != null) {
			for (int index = 0; index < etag_list.size(); ++index) {
				if (Arrays.equals(etag_list.get(index), etag)) {
					etag_list.remove(index);
					break;
				}
			}
		}
		return this;
	}

	/**
	 * Removes all ETag options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearETags() {
		getETags().clear();
		return this;
	}

	/**
	 * Checks if the If-None-Match option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasIfNoneMatch() {
		return if_none_match;
	}

	/**
	 * Sets or unsets the If-None-Match option.
	 * 
	 * @param present the presence of the option
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setIfNoneMatch(boolean present) {
		if_none_match = present;
		return this;
	}

	/**
	 * Gets the uint value of the Uri-Port option.
	 * 
	 * @return the Uri-Port value or null if the option is not present
	 */
	public Integer getUriPort() {
		return uri_port;
	}

	/**
	 * Checks if the Uri-Port option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasUriPort() {
		return uri_port != null;
	}

	/**
	 * Sets the Uri-Port option.
	 * 
	 * @param port the Uri-Port value to set.
	 * @return this OptionSet for a fluent API.
	 * @throws IllegalArgumentException if port is not in valid range
	 */
	public OptionSet setUriPort(int port) {
		OptionNumberRegistry.assertValue(OptionNumberRegistry.URI_PORT, port);
		this.uri_port = port;
		return this;
	}

	/**
	 * Removes the Uri-Port option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeUriPort() {
		uri_port = null;
		return this;
	}

	/**
	 * Gets the list of Location-Path segment strings.
	 * <p>
	 * The OptionSet uses lazy initialization for this list.
	 * 
	 * @return the list of Location-Path segments
	 */
	public List<String> getLocationPath() {
		synchronized (this) {
			if (location_path_list == null)
				location_path_list = new LinkedList<String>();
		}
		return location_path_list;
	}

	/**
	 * Gets the Location-Path and Location-Query options as relative URI string.
	 * 
	 * @return the Location-* as string
	 */
	public String getLocationString() {
		StringBuilder builder = new StringBuilder();
		builder.append('/');
		appendMultiOption(builder, getLocationPath(), '/');
		if (getLocationQueryCount() > 0) {
			builder.append('?');
			appendMultiOption(builder, getLocationQuery(), '&');
		}
		return builder.toString();
	}

	/**
	 * Gets the Location-Path options as relative URI string.
	 * 
	 * @return the Location-Path as string
	 */
	public String getLocationPathString() {
		return getMultiOptionString(getLocationPath(), '/');
	}

	/**
	 * Gets the number of Location-Path options (i.e., path segments).
	 * 
	 * @return the count
	 */
	public int getLocationPathCount() {
		return getLocationPath().size();
	}

	/**
	 * Adds a path segment to the Location-Path options.
	 * 
	 * @param segment the path segment to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the segment is {@code null}
	 * @throws IllegalArgumentException if the segment has more than 255 bytes.
	 */
	public OptionSet addLocationPath(String segment) {
		checkOptionValue(OptionNumberRegistry.LOCATION_PATH, segment);
		getLocationPath().add(segment);
		return this;
	}

	/**
	 * Removes all Location-Path options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearLocationPath() {
		getLocationPath().clear();
		return this;
	}

	/**
	 * Sets the complete relative Location-Path.
	 * 
	 * @param path the Location-Path to set
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the path is {@code null}
	 * @throws IllegalArgumentException if one of the path's segments has more
	 *             than 255 bytes.
	 */
	public OptionSet setLocationPath(String path) {
		final String slash = "/";

		// remove leading slash
		if (path.startsWith(slash)) {
			path = path.substring(slash.length());
		}

		clearLocationPath();

		for (String segment : path.split(slash)) {
			// empty path segments are allowed (e.g., /test vs /test/)
			addLocationPath(segment);
		}
		return this;
	}

	/**
	 * Gets the URI-Path and URI-Query options as relative URI string.
	 * 
	 * @return the URI-* as string
	 */
	public String getUriString() {
		StringBuilder builder = new StringBuilder();
		builder.append('/');
		appendMultiOption(builder, getUriPath(), '/');
		if (getURIQueryCount() > 0) {
			builder.append('?');
			appendMultiOption(builder, getUriQuery(), '&');
		}
		return builder.toString();
	}

	/**
	 * Gets the list of Uri-Path segment strings.
	 * <p>
	 * The OptionSet uses lazy initialization for this list.
	 * 
	 * @return the list of Uri-Path segments
	 */
	public List<String> getUriPath() {
		synchronized (this) {
			if (uri_path_list == null)
				uri_path_list = new LinkedList<String>();
		}
		return uri_path_list;
	}

	/**
	 * Gets the Uri-Path options as relative URI string.
	 * <p>
	 * To ease splitting, it omits the leading slash.
	 * 
	 * @return the Uri-Path as string
	 */
	public String getUriPathString() {
		return getMultiOptionString(getUriPath(), '/');
	}

	/**
	 * Returns the number of Uri-Path options (i.e., path segments).
	 * 
	 * @return the count
	 */
	public int getURIPathCount() {
		return getUriPath().size();
	}

	/**
	 * Sets the complete relative Uri-Path.
	 * 
	 * @param path the Uri-Path to set
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the path is {@code null}
	 * @throws IllegalArgumentException if one of the path's segments has more
	 *             than 255 bytes.
	 */
	public OptionSet setUriPath(String path) {
		final String slash = "/";

		// remove leading slash
		if (path.startsWith(slash)) {
			path = path.substring(slash.length());
		}

		clearUriPath();

		for (String segment : path.split(slash)) {
			// empty path segments are allowed (e.g., /test vs /test/)
			addUriPath(segment);
		}
		return this;
	}

	/**
	 * Adds a path segment to the Uri-Path options.
	 * 
	 * @param segment the path segment to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the segment is {@code null}
	 * @throws IllegalArgumentException if the segment has more than 255 bytes.
	 */
	public OptionSet addUriPath(String segment) {
		checkOptionValue(OptionNumberRegistry.URI_PATH, segment);
		getUriPath().add(segment);
		return this;
	}

	/**
	 * Removes all Uri-Path options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearUriPath() {
		getUriPath().clear();
		return this;
	}

	/**
	 * Gets the Content-Format Identifier of the Content-Format option (see
	 * <a href=
	 * "http://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats">IANA
	 * Registry</a>).
	 * 
	 * @return the ID as int, or, {@code -1}, if undefined
	 */
	public int getContentFormat() {
		return hasContentFormat() ? content_format : MediaTypeRegistry.UNDEFINED;
	}

	/**
	 * Checks if the Content-Format option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasContentFormat() {
		return content_format != null;
	}

	/**
	 * Compares the Content-Format option value to a given format.
	 * <p>
	 * Can be used by a server to check the Content-Format of a request body or
	 * by a client to check the Content-Format of a response body.
	 * 
	 * @param format the Content-Format ID to compare with
	 * @return {@code true}, if equal
	 * @see MediaTypeRegistry
	 */
	public boolean isContentFormat(int format) {
		return content_format != null && content_format == format;
	}

	/**
	 * Sets the Content-Format ID of the Content-Format option (see <a href=
	 * "http://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats">IANA
	 * Registry</a>).
	 * 
	 * @param format the Content-Format ID. Use value
	 *            {@link MediaTypeRegistry#UNDEFINED} to
	 *            {@link #removeContentFormat()}.
	 * @return this OptionSet for a fluent API.
	 * @throws IllegalArgumentException if value is out of range {@code 0} to
	 *             {@link MediaTypeRegistry#MAX_TYPE} and not
	 *             {@link MediaTypeRegistry#UNDEFINED} (since 3.0).
	 * @see MediaTypeRegistry
	 */
	public OptionSet setContentFormat(int format) {
		if (MediaTypeRegistry.UNDEFINED == format) {
			content_format = null;
		} else {
			OptionNumberRegistry.assertValue(OptionNumberRegistry.CONTENT_FORMAT, format);
			content_format = format;
		}
		return this;
	}

	/**
	 * Removes the Content-Format option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeContentFormat() {
		content_format = null;
		return this;
	}

	/**
	 * Gets the value of the Max-Age option in seconds.
	 * 
	 * @return the Max-Age in seconds
	 */
	public Long getMaxAge() {
		Long m = max_age;
		return m != null ? m : OptionNumberRegistry.Defaults.MAX_AGE;
	}

	/**
	 * Checks if the Max-Age option is present.
	 * <p>
	 * If it is not present, the default value of 60 seconds applies.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasMaxAge() {
		return max_age != null;
	}

	/**
	 * Sets the Max-Age option.
	 * 
	 * @param age the Max-Age value in seconds
	 * @return this OptionSet for a fluent API.
	 * @throws IllegalArgumentException if the age has more than 4 bytes.
	 */
	public OptionSet setMaxAge(long age) {
		OptionNumberRegistry.assertValue(OptionNumberRegistry.MAX_AGE, age);
		max_age = age;
		return this;
	}

	/**
	 * Removes the Max-Age option. Returns the current OptionSet object for a
	 * fluent API.
	 * 
	 * @return this Optionset
	 */
	public OptionSet removeMaxAge() {
		max_age = null;
		return this;
	}

	/**
	 * Gets the list of Uri-Query arguments.
	 * <p>
	 * The OptionSet uses lazy initialization for this list.
	 * 
	 * @return the list of query arguments
	 */
	public List<String> getUriQuery() {
		synchronized (this) {
			if (uri_query_list == null)
				uri_query_list = new LinkedList<String>();
		}
		return uri_query_list;
	}

	/**
	 * Gets the number of Uri-Query options (i.e., arguments).
	 * 
	 * @return the count
	 */
	public int getURIQueryCount() {
		return getUriQuery().size();
	}

	/**
	 * Gets the Uri-Query options as &amp;-separated query string.
	 * 
	 * @return the Uri-Query as string
	 */
	public String getUriQueryString() {
		return getMultiOptionString(getUriQuery(), '&');
	}

	/**
	 * Sets the complete Uri-Query through a &amp;-separated list of arguments.
	 * 
	 * @param query the Query string
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the query is {@code null}
	 * @throws IllegalArgumentException if one of the query's arguments has more
	 *             than 255 bytes.
	 */
	public OptionSet setUriQuery(String query) {
		while (query.startsWith("?"))
			query = query.substring(1);

		clearUriQuery();

		for (String segment : query.split("&")) {
			if (!segment.isEmpty()) {
				addUriQuery(segment);
			}
		}
		return this;
	}

	/**
	 * Adds an argument to the Uri-Query options.
	 * 
	 * @param argument the argument to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the argument is {@code null}
	 * @throws IllegalArgumentException if the argument has more than 255 bytes.
	 */
	public OptionSet addUriQuery(String argument) {
		checkOptionValue(OptionNumberRegistry.URI_QUERY, argument);
		getUriQuery().add(argument);
		uri_query_parameter = null;
		return this;
	}

	/**
	 * Removes a specific argument from the Uri-Query options.
	 * 
	 * @param argument the argument to remove
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeUriQuery(String argument) {
		if (getUriQuery().remove(argument)) {
			uri_query_parameter = null;
		}
		return this;
	}

	/**
	 * Removes all Uri-Query options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearUriQuery() {
		getUriQuery().clear();
		uri_query_parameter = null;
		return this;
	}

	/**
	 * Gets the Uri-Query parameter.
	 * <p>
	 * The OptionSet uses lazy initialization for this map.
	 * 
	 * @return the map of Uri-Query parameter
	 * @see #getUriQueryParameter(List, List)
	 * @since 3.8
	 */
	public UriQueryParameter getUriQueryParameter() {
		if (uri_query_parameter == null) {
			return getUriQueryParameter(null, null);
		} else {
			return uri_query_parameter;
		}
	}

	/**
	 * Gets the Uri-Query parameter.
	 * <p>
	 * 
	 * @param supportedParameterNames list of supported parameter names. May be
	 *            {@code null} or empty, if the parameter names should not be verified.
	 * @return the map of Uri-Query parameter
	 * @throws IllegalArgumentException if a provided query parameter could not
	 *             be verified.
	 * @see #getUriQueryParameter(List, List)
	 * @since 3.8
	 */
	public UriQueryParameter getUriQueryParameter(List<String> supportedParameterNames) {
		return getUriQueryParameter(supportedParameterNames, null);
	}

	/**
	 * Gets the Uri-Query parameter.
	 * <p>
	 * 
	 * @param supportedParameterNames list of supported parameter names. May be
	 *            {@code null} or empty, if the parameter names should not be
	 *            verified.
	 * @param unsupportedParameter list to add the unsupported parameter. May be
	 *            {@code null}, if unsupported parameter names should cause a
	 *            {@link IllegalArgumentException}.
	 * @return the map of Uri-Query parameter
	 * @throws IllegalArgumentException if a provided query parameter could not
	 *             be verified and no list for unsupported parameter is
	 *             provided.
	 * @see #getUriQueryParameter()
	 * @see #getUriQueryParameter(List)
	 * @see UriQueryParameter
	 * @since 3.8
	 */
	public UriQueryParameter getUriQueryParameter(List<String> supportedParameterNames,
			List<String> unsupportedParameter) {
		if (uri_query_list != null && !uri_query_list.isEmpty()) {
			uri_query_parameter = new UriQueryParameter(uri_query_list, supportedParameterNames, unsupportedParameter);
		} else {
			uri_query_parameter = UriQueryParameter.EMPTY;
		}
		return uri_query_parameter;
	}

	/**
	 * Gets the Content-Format Identifier of the Accept option (see <a href=
	 * "http://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats">IANA
	 * Registry</a>).
	 * 
	 * @return the ID as int, or, {@code -1}, if undefined
	 */
	public int getAccept() {
		return hasAccept() ? accept : MediaTypeRegistry.UNDEFINED;
	}

	/**
	 * Checks if the Accept option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasAccept() {
		return accept != null;
	}

	/**
	 * Compares the Accept option value to a given format.
	 * 
	 * @param format the Content-Format ID to compare with
	 * @return {@code true}, if equal
	 */
	public boolean isAccept(int format) {
		return accept != null && accept == format;
	}

	/**
	 * Sets the Content-Format ID of the Accept option (see <a href=
	 * "http://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats">IANA
	 * Registry</a>).
	 * 
	 * @param format the Content-Format ID
	 * @return this OptionSet for a fluent API.
	 * @throws IllegalArgumentException if value is out of range {@code 0} to
	 *             {@link MediaTypeRegistry#MAX_TYPE}.
	 * @see MediaTypeRegistry
	 */
	public OptionSet setAccept(int format) {
		OptionNumberRegistry.assertValue(OptionNumberRegistry.ACCEPT, format);
		accept = format;
		return this;
	}

	/**
	 * Removes the Accept option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeAccept() {
		accept = null;
		return this;
	}

	/**
	 * Gets the list of Location-Query arguments.
	 * <p>
	 * The OptionSet uses lazy initialization for this list.
	 * 
	 * @return the list of query arguments
	 */
	public List<String> getLocationQuery() {
		synchronized (this) {
			if (location_query_list == null)
				location_query_list = new LinkedList<String>();
		}
		return location_query_list;
	}

	/**
	 * Gets the number of Location-Query options (i.e., arguments).
	 * 
	 * @return the count
	 */
	public int getLocationQueryCount() {
		return getLocationQuery().size();
	}

	/**
	 * Gets the Location-Query options as &amp;-separated list string.
	 * 
	 * @return the Location-Query as string
	 */
	public String getLocationQueryString() {
		return getMultiOptionString(getLocationQuery(), '&');
	}

	/**
	 * Sets the complete Location-Query through a &amp;-separated list of
	 * arguments.
	 * 
	 * @param query the Location-Query string
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the query is {@code null}
	 * @throws IllegalArgumentException if one of the query's arguments has more
	 *             than 255 bytes.
	 */
	public OptionSet setLocationQuery(String query) {
		while (query.startsWith("?"))
			query = query.substring(1);

		clearLocationQuery();

		for (String segment : query.split("&")) {
			if (!segment.isEmpty()) {
				addLocationQuery(segment);
			}
		}
		return this;
	}

	/**
	 * Adds an argument to the Location-Query options.
	 * 
	 * @param argument the argument to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the argument is {@code null}
	 * @throws IllegalArgumentException if the argument has more than 255 bytes.
	 */
	public OptionSet addLocationQuery(String argument) {
		checkOptionValue(OptionNumberRegistry.LOCATION_QUERY, argument);
		getLocationQuery().add(argument);
		return this;
	}

	/**
	 * Removes a specific argument from the Location-Query options.
	 * 
	 * @param argument the argument to remove
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeLocationQuery(String argument) {
		getLocationQuery().remove(argument);
		return this;
	}

	/**
	 * Gets all Location-Query options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet clearLocationQuery() {
		getLocationQuery().clear();
		return this;
	}

	/**
	 * Gets the string value of the Proxy-Uri option.
	 * 
	 * @return the Proxy-Uri or null if the option is not present
	 */
	public String getProxyUri() {
		return proxy_uri;
	}

	/**
	 * Checks if the Proxy-Uri option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasProxyUri() {
		return proxy_uri != null;
	}

	/**
	 * Sets the Proxy-Uri option.
	 * 
	 * @param uri the Proxy-Uri value to set.
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the uri is {@code null}
	 * @throws IllegalArgumentException if the uri has less than 1 or more than
	 *             1034 bytes.
	 */
	public OptionSet setProxyUri(String uri) {
		checkOptionValue(OptionNumberRegistry.PROXY_URI, uri);
		proxy_uri = uri;
		return this;
	}

	/**
	 * Removes the Proxy-Uri option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeProxyUri() {
		proxy_uri = null;
		return this;
	}

	/**
	 * Gets the string value of the Proxy-Scheme option.
	 * 
	 * @return the Proxy-Scheme or null if the option is not present
	 */
	public String getProxyScheme() {
		return proxy_scheme;
	}

	/**
	 * Checks if the Proxy-Scheme option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasProxyScheme() {
		return proxy_scheme != null;
	}

	/**
	 * Sets the Proxy-Scheme option.
	 * 
	 * @param scheme the Proxy-Scheme value to set.
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the scheme is {@code null}
	 * @throws IllegalArgumentException if the scheme has less than 1 or more
	 *             than 255 bytes.
	 */
	public OptionSet setProxyScheme(String scheme) {
		checkOptionValue(OptionNumberRegistry.PROXY_SCHEME, scheme);
		proxy_scheme = scheme;
		return this;
	}

	/**
	 * Removes the Proxy-Scheme option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeProxyScheme() {
		proxy_scheme = null;
		return this;
	}

	/**
	 * Gets the Block1 option.
	 * 
	 * @return the BlockOption
	 */
	public BlockOption getBlock1() {
		return block1;
	}

	/**
	 * Checks if the Block1 option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasBlock1() {
		return block1 != null;
	}

	/**
	 * Sets the Block1 option.
	 * 
	 * @param szx the block size
	 * @param m the more flag
	 * @param num the block number
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock1(int szx, boolean m, int num) {
		this.block1 = new BlockOption(szx, m, num);
		return this;
	}

	/**
	 * Sets the Block1 option.
	 * 
	 * @param value the encoded value
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock1(byte[] value) {
		this.block1 = new BlockOption(value);
		return this;
	}

	/**
	 * Sets the Block1 option.
	 * 
	 * @param block the block object
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock1(BlockOption block) {
		this.block1 = block;
		return this;
	}

	/**
	 * Removes the Block1 option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeBlock1() {
		this.block1 = null;
		return this;
	}

	/**
	 * Gets the Block2 option.
	 * 
	 * @return the BlockOption
	 */
	public BlockOption getBlock2() {
		return block2;
	}

	/**
	 * Checks if the Block2 option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasBlock2() {
		return block2 != null;
	}

	/**
	 * Sets the Block2 option.
	 * 
	 * @param szx the block size
	 * @param m the more flag
	 * @param num the block number
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock2(int szx, boolean m, int num) {
		this.block2 = new BlockOption(szx, m, num);
		return this;
	}

	/**
	 * Sets the Block2 option.
	 * 
	 * @param value the encoded value
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock2(byte[] value) {
		this.block2 = new BlockOption(value);
		return this;
	}

	/**
	 * Sets the Block2 option.
	 * 
	 * @param block the block object
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setBlock2(BlockOption block) {
		this.block2 = block;
		return this;
	}

	/**
	 * Removes the Block2 option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeBlock2() {
		this.block2 = null;
		return this;
	}

	/**
	 * Gets the uint value of the Size1 option.
	 * 
	 * @return the Size1 value, or, {@code null}, if the option is not present
	 */
	public Integer getSize1() {
		return size1;
	}

	/**
	 * Checks if the Size1 option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasSize1() {
		return size1 != null;
	}

	/**
	 * Sets the Size1 option value.
	 * 
	 * @param size the size of the request body
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setSize1(int size) {
		this.size1 = size;
		return this;
	}

	/**
	 * Removes the Size1 option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeSize1() {
		this.size1 = null;
		return this;
	}

	/**
	 * Gets the uint value of the Size2 option.
	 * 
	 * @return the Size2 value, or, {@code null}, if the option is not present
	 */
	public Integer getSize2() {
		return size2;
	}

	/**
	 * Checks if the Size2 option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasSize2() {
		return size2 != null;
	}

	/**
	 * Sets the Size2 option value.
	 * 
	 * @param size the size of the response body
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet setSize2(int size) {
		this.size2 = size;
		return this;
	}

	/**
	 * Removes the Size2 option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeSize2() {
		this.size2 = null;
		return this;
	}

	/**
	 * Gets the uint value of the Observe option.
	 * 
	 * @return the Observe value, or, {@code null}, if the option is not present
	 */
	public Integer getObserve() {
		return observe;
	}

	/**
	 * Checks if the Observe option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasObserve() {
		return observe != null;
	}

	/**
	 * Sets the Observe option value.
	 * 
	 * @param seqnum the sequence number
	 * @return this OptionSet for a fluent API.
	 * @throws IllegalArgumentException if the given number is &lt; 0 or &gt;
	 *             2^24 - 1
	 */
	public OptionSet setObserve(final int seqnum) {
		OptionNumberRegistry.assertValue(OptionNumberRegistry.OBSERVE, seqnum);
		this.observe = seqnum;
		return this;
	}

	/**
	 * Removes the Observe option.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeObserve() {
		observe = null;
		return this;
	}

	/**
	 * Checks if a given number is a valid value for the <em>Observe</em>
	 * option.
	 * 
	 * @param value The value to check.
	 * @return {@code true}, if the value is &gt; 0 and &lt; 2^24 - 1.
	 */
	public static boolean isValidObserveOption(final int value) {
		return value >= 0 && value <= MAX_OBSERVE_NO;
	}

	/**
	 * Gets the byte array value of the OSCore option.
	 * 
	 * @return the OSCore value or null if the option is not present
	 */
	public byte[] getOscore() {
		return oscore;
	}

	/**
	 * Checks if the OSCore option is present.
	 * 
	 * @return {@code true}, if present
	 */
	public boolean hasOscore() {
		return oscore != null;
	}

	/**
	 * Replaces the Oscore option with oscore.
	 * 
	 * @param oscore the new Oscore value
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if the oscore is {@code null}
	 * @throws IllegalArgumentException if the oscore has more than 255 bytes.
	 */
	public OptionSet setOscore(byte[] oscore) {
		checkOptionValue(OptionNumberRegistry.OSCORE, oscore);
		this.oscore = oscore.clone();
		return this;
	}

	/**
	 * Removes the OSCore options.
	 * 
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet removeOscore() {
		oscore = null;
		return this;
	}

	/**
	 * Gets the NoResponse option.
	 * 
	 * @return the NoResponse option, or, {@code null}, if the option is not present
	 * @since 3.0
	 */
	public NoResponseOption getNoResponse() {
		return no_response;
	}

	/**
	 * Checks, if the NoResponse option is present.
	 * 
	 * @return {@code true}, if present
	 * @since 3.0
	 */
	public boolean hasNoResponse() {
		return no_response != null;
	}

	/**
	 * Sets the NoResponse option value.
	 * 
	 * @param noResponse the NoResponse pattern
	 * @return this OptionSet for a fluent API.
	 * @since 3.0
	 */
	public OptionSet setNoResponse(int noResponse) {
		this.no_response = new NoResponseOption(noResponse);
		return this;
	}

	/**
	 * Sets the NoResponse option value.
	 * 
	 * @param noResponse the NoResponse option
	 * @return this OptionSet for a fluent API.
	 * @since 3.0
	 */
	public OptionSet setNoResponse(NoResponseOption noResponse) {
		this.no_response = noResponse;
		return this;
	}

	/**
	 * Removes the NoResponse option.
	 * 
	 * @return this OptionSet for a fluent API.
	 * @since 3.0
	 */
	public OptionSet removeNoResponse() {
		this.no_response = null;
		return this;
	}

	/**
	 * Checks, if an arbitrary option is present.
	 * 
	 * Note: implementation uses {@link #asSortedList()} and is therefore not
	 * recommended to be called too frequently.
	 * 
	 * @param number the option number
	 * @return {@code true}, if present
	 */
	public boolean hasOption(int number) {
		return Collections.binarySearch(asSortedList(), new Option(number)) >= 0;
	}

	private List<Option> getOthersInternal() {
		synchronized (this) {
			if (others == null)
				others = new LinkedList<Option>();
		}
		return others;
	}

	/**
	 * Gets list of other options.
	 * 
	 * @return an unmodifiable and unsorted list of other options.
	 */
	public List<Option> getOthers() {
		List<Option> others = this.others;
		if (others == null) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(others);
		}
	}

	/**
	 * Gets list of other options.
	 * 
	 * @param number other option
	 * @return an unmodifiable and unsorted list of other options with the provided number.
	 * @since 3.7
	 */
	public List<Option> getOthers(int number) {
		List<Option> options = null;
		List<Option> others = this.others;
		if (others != null) {
			for (Option option : others) {
				if (option.getNumber() == number) {
					if (options == null) {
						options = new ArrayList<>();
					}
					options.add(option);
				}
			}
		}
		if (options == null) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(options);
		}
	}

	/**
	 * Gets other option.
	 * 
	 * If the other option is contained more than once, return the first.
	 * 
	 * @param number other option
	 * @return other option, or {@code null}, if not available.
	 * @since 3.7
	 */
	public Option getOtherOption(int number) {
		List<Option> others = this.others;
		if (others != null) {
			for (Option option : others) {
				if (option.getNumber() == number) {
					return option;
				}
			}
		}
		return null;
	}

	/**
	 * Gets all options in a list sorted according to their option number.
	 * <p>
	 * The list cannot be use to modify the OptionSet of the message, since it
	 * is a copy.
	 * 
	 * @return the sorted list (a copy)
	 */
	public List<Option> asSortedList() {
		ArrayList<Option> options = new ArrayList<Option>();

		if (if_match_list != null)
			for (byte[] value : if_match_list)
				options.add(new Option(OptionNumberRegistry.IF_MATCH, value));
		if (hasUriHost())
			options.add(new Option(OptionNumberRegistry.URI_HOST, getUriHost()));
		if (etag_list != null)
			for (byte[] value : etag_list)
				options.add(new Option(OptionNumberRegistry.ETAG, value));
		if (hasIfNoneMatch())
			options.add(new Option(OptionNumberRegistry.IF_NONE_MATCH, Bytes.EMPTY));
		if (hasUriPort())
			options.add(new Option(OptionNumberRegistry.URI_PORT, getUriPort()));
		if (location_path_list != null)
			for (String str : location_path_list)
				options.add(new Option(OptionNumberRegistry.LOCATION_PATH, str));
		if (uri_path_list != null)
			for (String str : uri_path_list)
				options.add(new Option(OptionNumberRegistry.URI_PATH, str));
		if (hasContentFormat())
			options.add(new Option(OptionNumberRegistry.CONTENT_FORMAT, getContentFormat()));
		if (hasMaxAge())
			options.add(new Option(OptionNumberRegistry.MAX_AGE, getMaxAge()));
		if (uri_query_list != null)
			for (String str : uri_query_list)
				options.add(new Option(OptionNumberRegistry.URI_QUERY, str));
		if (hasAccept())
			options.add(new Option(OptionNumberRegistry.ACCEPT, getAccept()));
		if (location_query_list != null)
			for (String str : location_query_list)
				options.add(new Option(OptionNumberRegistry.LOCATION_QUERY, str));
		if (hasProxyUri())
			options.add(new Option(OptionNumberRegistry.PROXY_URI, getProxyUri()));
		if (hasProxyScheme())
			options.add(new Option(OptionNumberRegistry.PROXY_SCHEME, getProxyScheme()));

		if (hasObserve())
			options.add(new Option(OptionNumberRegistry.OBSERVE, getObserve()));

		if (hasBlock1())
			options.add(new Option(OptionNumberRegistry.BLOCK1, getBlock1().getValue()));
		if (hasBlock2())
			options.add(new Option(OptionNumberRegistry.BLOCK2, getBlock2().getValue()));
		if (hasSize1())
			options.add(new Option(OptionNumberRegistry.SIZE1, getSize1()));
		if (hasSize2())
			options.add(new Option(OptionNumberRegistry.SIZE2, getSize2()));
		if (hasOscore())
			options.add(new Option(OptionNumberRegistry.OSCORE, getOscore()));
		if (hasNoResponse())
			options.add(getNoResponse().toOption());

		if (others != null)
			options.addAll(others);

		Collections.sort(options);
		return options;
	}

	/**
	 * Add options.
	 * 
	 * @param options list with options to add
	 * @return this OptionSet for a fluent API.
	 * @since 3.0
	 */
	public OptionSet addOptions(Option... options) {
		if (options != null) {
			for (Option option : options) {
				addOption(option);
			}
		}
		return this;
	}

	/**
	 * Add options.
	 * 
	 * @param options list with options to add
	 * @return this OptionSet for a fluent API.
	 * @since 3.0
	 */
	public OptionSet addOptions(List<Option> options) {
		if (options != null) {
			for (Option option : options) {
				addOption(option);
			}
		}
		return this;
	}

	/**
	 * Adds an arbitrary option.
	 * <p>
	 * Known options are checked if they are repeatable.
	 * 
	 * @param option the Option object to add
	 * @return this OptionSet for a fluent API.
	 */
	public OptionSet addOption(Option option) {
		switch (option.getNumber()) {
		case OptionNumberRegistry.IF_MATCH:
			addIfMatch(option.getValue());
			break;
		case OptionNumberRegistry.URI_HOST:
			setUriHost(option.getStringValue());
			break;
		case OptionNumberRegistry.ETAG:
			addETag(option.getValue());
			break;
		case OptionNumberRegistry.IF_NONE_MATCH:
			setIfNoneMatch(true);
			break;
		case OptionNumberRegistry.URI_PORT:
			setUriPort(option.getIntegerValue());
			break;
		case OptionNumberRegistry.LOCATION_PATH:
			addLocationPath(option.getStringValue());
			break;
		case OptionNumberRegistry.URI_PATH:
			addUriPath(option.getStringValue());
			break;
		case OptionNumberRegistry.CONTENT_FORMAT:
			setContentFormat(option.getIntegerValue());
			break;
		case OptionNumberRegistry.MAX_AGE:
			setMaxAge(option.getLongValue());
			break;
		case OptionNumberRegistry.URI_QUERY:
			addUriQuery(option.getStringValue());
			break;
		case OptionNumberRegistry.ACCEPT:
			setAccept(option.getIntegerValue());
			break;
		case OptionNumberRegistry.LOCATION_QUERY:
			addLocationQuery(option.getStringValue());
			break;
		case OptionNumberRegistry.PROXY_URI:
			setProxyUri(option.getStringValue());
			break;
		case OptionNumberRegistry.PROXY_SCHEME:
			setProxyScheme(option.getStringValue());
			break;
		case OptionNumberRegistry.BLOCK1:
			setBlock1(option.getValue());
			break;
		case OptionNumberRegistry.BLOCK2:
			setBlock2(option.getValue());
			break;
		case OptionNumberRegistry.SIZE1:
			setSize1(option.getIntegerValue());
			break;
		case OptionNumberRegistry.SIZE2:
			setSize2(option.getIntegerValue());
			break;
		case OptionNumberRegistry.OBSERVE:
			setObserve(option.getIntegerValue());
			break;
		case OptionNumberRegistry.OSCORE:
			setOscore(option.getValue());
			break;
		case OptionNumberRegistry.NO_RESPONSE:
			setNoResponse(option.getIntegerValue());
			break;
		default:
			getOthersInternal().add(option);
		}
		return this;
	}

	/**
	 * Add other option bypassing the validation check.
	 * 
	 * If standard options are added by this function, the additional validation
	 * checks are bypassed! That maybe used for tests, but will result in
	 * failing communication, if used for something else. Please use
	 * {@link #addOption(Option)} for all options, including others, which are
	 * not intended for tests.
	 * 
	 * If custom options are added by this function, the additional validation
	 * will be applied based on a {@link CustomOptionNumberRegistry}.
	 * 
	 * @param option the Option object to add
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if option is {@code null}.
	 * @see Option#setValueUnchecked(byte[])
	 * @since 3.7 (throws NullPointerException)
	 */
	public OptionSet addOtherOption(Option option) {
		if (option == null) {
			throw new NullPointerException("Option must not be null!");
		}
		int number = option.getNumber();
		if (OptionNumberRegistry.isCustomOption(number)) {
			OptionNumberRegistry.assertValueLength(number, option.getLength());
		}
		List<Option> others = getOthersInternal();
		if (OptionNumberRegistry.isSingleValue(number)) {
			for (int index=0; index < others.size(); ++index) {
				if (others.get(index).getNumber() == number) {
					others.remove(index);
					break;
				}
			}
		}
		others.add(option);
		return this;
	}

	/**
	 * Clear other option by value.
	 * 
	 * Note: the removing is based on {@link Option#equals(Object)}, which
	 * includes the value as well. For repeatable options all are removed,
	 * if the options are equal.
	 * 
	 * @param option other option
	 * @return this OptionSet for a fluent API.
	 * @throws NullPointerException if option is {@code null}.
	 * @see #clearOtherOption(int)
	 * @since 3.7
	 */
	public OptionSet clearOtherOption(Option option) {
		if (option == null) {
			throw new NullPointerException("Option must not be null!");
		}
		Iterator<Option> iterator = getOthersInternal().iterator();
		while (iterator.hasNext()) {
			Option currentOption = iterator.next();
			if (currentOption.equals(option)) {
				iterator.remove();
			}
		}
		return this;
	}

	/**
	 * Clear other option by number.
	 * 
	 * Note: the removing is based on {@link Option#getNumber()}. For repeatable
	 * options all are removed, if the number is matching.
	 * 
	 * @param number other option number
	 * @return this OptionSet for a fluent API.
	 * @see #clearOtherOption(Option)
	 * @since 3.7
	 */
	public OptionSet clearOtherOption(int number) {
		Iterator<Option> iterator = getOthersInternal().iterator();
		while (iterator.hasNext()) {
			Option currentOption = iterator.next();
			if (currentOption.getNumber() == number) {
				iterator.remove();
			}
		}
		return this;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		StringBuilder sbv = new StringBuilder();
		int oldNr = -1;
		boolean list = false;

		sb.append('{');

		for (Option opt : asSortedList()) {
			if (opt.getNumber() != oldNr) {
				if (oldNr != -1) {
					if (list)
						sbv.append(']');
					sb.append(sbv.toString()).append(", ");
					sbv.setLength(0);
				}
				list = false;

				sb.append('"');
				sb.append(OptionNumberRegistry.toString(opt.getNumber()));
				sb.append('"');
				sb.append(':');
			} else {
				if (!list)
					sbv.insert(0, '[');
				list = true;
				sbv.append(",");
			}
			sbv.append(opt.toValueString());

			oldNr = opt.getNumber();
		}
		if (list)
			sbv.append(']');
		sb.append(sbv.toString());
		sb.append('}');

		return sb.toString();
	}

	/**
	 * Gets multiple option as string.
	 * 
	 * @param multiOption multiple option as list of strings
	 * @param separator separator for options
	 * @return multiple option as string
	 */
	private String getMultiOptionString(List<String> multiOption, char separator) {
		StringBuilder builder = new StringBuilder();
		appendMultiOption(builder, multiOption, separator);
		return builder.toString();
	}

	/**
	 * Appends multiple option to string builder.
	 * 
	 * @param builder builder to append the multiple options.
	 * @param multiOption multiple option as list of strings
	 * @param separator separator for options
	 */
	private void appendMultiOption(StringBuilder builder, List<String> multiOption, char separator) {
		if (!multiOption.isEmpty()) {
			for (String optionText : multiOption) {
				builder.append(optionText).append(separator);
			}
			builder.setLength(builder.length() - 1);
		}
	}

	/**
	 * Check option value.
	 * 
	 * @param optionNumber option number
	 * @param value value of option
	 * @throws NullPointerException if provided value is {@code null}
	 * @throws IllegalArgumentException if provided value encoded in UTF-8 is
	 *             out of the provided range.
	 * @since 3.0
	 */
	private static void checkOptionValue(int optionNumber, String value) {
		checkOptionValue(optionNumber, value == null ? null : value.getBytes(CoAP.UTF8_CHARSET));
	}

	/**
	 * Check option value.
	 * 
	 * @param optionNumber option number
	 * @param value value of option
	 * @throws NullPointerException if provided value is {@code null}
	 * @throws IllegalArgumentException if provided value encoded in UTF-8 is
	 *             out of the provided range.
	 * @since 3.0
	 */
	private static void checkOptionValue(int optionNumber, byte[] value) {
		if (value == null) {
			String optionName = OptionNumberRegistry.toString(optionNumber);
			throw new NullPointerException(optionName + " option must not be null!");
		}
		OptionNumberRegistry.assertValueLength(optionNumber, value.length);
	}
}
