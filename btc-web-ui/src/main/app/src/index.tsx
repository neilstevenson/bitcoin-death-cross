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

'use strict';

import React from "react";
import { createRoot } from 'react-dom/client';
import Alerts from "./components/alerts/Alerts";
import Finos from "./components/finos/Finos";

const alerts = React.createElement(Alerts)
const finos = React.createElement(Finos)

const App = (): React.ReactElement => {
    return React.createElement(
	    "div",
	    null,
	    React.createElement("div", "alerts_pane", alerts),
	    React.createElement("hr", "pane_separator", null),
	    React.createElement("div", "finos_pane", finos),
	);
};

window.addEventListener("load", () => {
	const rootContainer = document.getElementById('root');
    const root = createRoot(rootContainer!);
    root.render(<App />);
});
