/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hz.demos;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * <p>A Spring service for file access convenience.
 * </p>
 */
@Service
public class FileUtils {

	@Autowired
	private ApplicationContext applicationContext;

	/**
	 * <p> Find CSV files in "{@code src/main/resources}".
	 * </p>
	 *
	 * @return An array of files of CSV data.
	 * @throws Exception
	 */
	public List<Resource> getResources() throws Exception {
		Resource[] resources = this.applicationContext.getResources("classpath:**/*.csv");
		return Arrays.asList(resources);
	}
}
