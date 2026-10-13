<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Dynamic Boost Test Page</title>
    <link rel="stylesheet" href="https://code.jquery.com/ui/1.14.1/themes/base/jquery-ui.css">
    <script src="https://code.jquery.com/jquery-3.7.1.js"></script>
    <script src="https://code.jquery.com/ui/1.14.1/jquery-ui.js"></script>
    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>

    <style>
        body, table {
            font-family: "Arial", serif;
            font-size: small
        }
        fieldset {
            width: 92%;
        }
        .button_panel {
            margin: 5px;
        }
        .chart {
            height: 30%
        }
        .ui-controlgroup-item {
            margin-right: 5px !important;
            margin-top: 5px !important;
        }
        .ui-dialog {
            background: #d5d5d5;
            box-shadow: 10px 10px 10px #00000050;
        }
        .button_panel {
            margin-top: 7px;
            float: right;
            margin-bottom: 0;
        }
        #config_dialog input {
                width: 60px;
        }
    </style>

    <script type="text/javascript">
        const maxRows = eval(${param.maxRows}) || 500;
        const campaignId = eval(${param.campaignId});
        const refreshRate = eval(${param.refreshRate}) || 1000;
        const defaultChartOptions = {
            curveType: 'none',
            hAxis: {
                format: 'HH:mm',
                textStyle: {
                    fontSize: 12
                }
            },
            vAxis: {
                textStyle: {
                    fontSize: 12
                }
            },
            legend: {
                position: 'bottom',
                textStyle: {
                    fontSize: 12
                }
            },
            explorer: {},
            chartArea: {top: 10, bottom: 55, left: 55, right: 55}
        }

        const failedCallsChartOptions = {...defaultChartOptions, ...{
                colors: ['#15cc00', '#f18400', '#b500c9'],
                series: {
                    0: {targetAxisIndex: 0, lineWidth: 1.5},
                    1: {targetAxisIndex: 0, lineWidth: 1.5},
                    2: {targetAxisIndex: 1, type: 'line'},
                },
                seriesType: 'steppedArea',
                vAxes: {
                    0: {
                        title: 'Calls count',
                        gridlines: {color: '#9f9f9f'},
                        minorGridlines: {count: 0}

                    },
                    1: {
                        title: 'Answer Rate',
                        viewWindow: {min: 0, max: 1},
                        ticks: [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1],
                        gridlines: {color: '#ffcfcf'}
                    }
                }
            }
        };
        const abandonedCallsChartOptions = {...defaultChartOptions, ...{
                colors: ['#ff006a', '#00a3c4', '#9900a9'],
                series: {
                    0: {targetAxisIndex: 0, lineWidth: 1.5},
                    1: {targetAxisIndex: 0, lineWidth: 1.5},
                    2: {targetAxisIndex: 1, type: 'line'},
                },
                seriesType: 'steppedArea',
                vAxes: {
                    0: {
                        title: 'Calls count',
                        gridlines: {color: '#9f9f9f'},
                        minorGridlines: {count: 0}
                    },
                    1: {
                        title: 'Abandon Rate',
                        viewWindow: {min: 0, max: 1},
                        ticks: [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1],
                        gridlines: {color: '#ffcfcf'}
                    }
                }
            }
        };
        const boostChartOptions = {...defaultChartOptions, ...{
                colors: ['#ff0000'],
                series: {
                    0: {targetAxisIndex: 1},
                }
            }
        };

        let dataRequesting = true;
        let failedCallsData;
        let abandonedCallsData;
        let boostData;
        let failedCallsChart;
        let abandonedCallsChart;
        let boostChart;
        let currentIntervalStartTime;

        function createFailedCallsChart() {
            failedCallsData.addRow([new Date(), 0, 0, 0]);
            failedCallsChart = createComboChart('failed_calls_chart', failedCallsData, failedCallsChartOptions);
        }

        function createAbandonedCallsChart() {
            abandonedCallsData.addRow([new Date(), 0, 0, 0]);
            abandonedCallsChart = createComboChart('abandoned_calls_chart', abandonedCallsData, abandonedCallsChartOptions);
        }

        function createBoostChart() {
            boostChart = createLineChart('boost_chart', boostData, boostChartOptions);
        }

        function createComboChart(elementId, data, options) {
            let chart = new google.visualization.ComboChart(document.getElementById(elementId));
            chart.draw(data, options);
            return chart;
        }

        function createLineChart(elementId, data, options) {
            let chart = new google.visualization.LineChart(document.getElementById(elementId));
            chart.draw(data, options);
            return chart;
        }

        function onGetData(data) {
            if (data && data.currentData) {
                if (data.currentData.calculationIntervalStartTime !== currentIntervalStartTime) {
                    currentIntervalStartTime = data.currentData.calculationIntervalStartTime;
                    if (data.previousData) {
                        let previousAnswerRate = getAnswerRate(data.previousData);
                        let previousAbandonRate = getAbandonRate(data.previousData);
                        // Replace data for previous period
                        populateGraphData(data.previousData, previousAnswerRate, previousAbandonRate, true, false);
                    }
                    // Populate new period
                    populateGraphData(data.currentData, null, null, false, true);
                    drawCharts(true, true, true);
                } else {
                    // Replace data for current period
                    populateGraphData(data.currentData, null, null, true, false);
                    drawCharts(true, true, false);
                }
            }
        }

        function onGetDataHistory(dataArray) {
            if (!Array.isArray(dataArray)) {
                return;
            } else if (dataArray.length > 0) {
                failedCallsData.removeRow(0);
                abandonedCallsData.removeRow(0);
                for (let data of dataArray) {
                    populateGraphData(data, getAnswerRate(data), getAbandonRate(data), false, true);
                }
                drawCharts(true, true, true);
            }
        }

        function populateGraphData(data, answerRate, abandonRate, replaceLastRow, populateBoost) {
            let time = new Date(data.calculationIntervalStartTime);
            if (failedCallsData) {
                if (replaceLastRow && failedCallsData.getNumberOfRows() > 0) {
                    failedCallsData.removeRow(failedCallsData.getNumberOfRows() - 1);
                }
                failedCallsData.addRow([time, data.answeredCallsCount, data.failedCallsCount, answerRate]);
                checkForMaxRowsCount(failedCallsData);
            }
            if (abandonedCallsData) {
                if (replaceLastRow && abandonedCallsData.getNumberOfRows() > 0) {
                    abandonedCallsData.removeRow(abandonedCallsData.getNumberOfRows() - 1);
                }
                abandonedCallsData.addRow([time, data.abandonedCallsCount, data.handledCallsCount, abandonRate]);
                checkForMaxRowsCount(abandonedCallsData);
            }
            if (boostData && populateBoost) {
                boostData.addRow([time, data.boostValue]);
                checkForMaxRowsCount(boostData);
            }
        }

        function drawCharts(drawFailedCalls, drawAbandonCalls, drawBoost) {
            if (drawFailedCalls && failedCallsData && failedCallsChart) {
                failedCallsChart.draw(failedCallsData, failedCallsChartOptions);
            }
            if (drawAbandonCalls && abandonedCallsData && abandonedCallsChart) {
                abandonedCallsChart.draw(abandonedCallsData, abandonedCallsChartOptions);
            }
            if (drawBoost && boostData && boostChart) {
                boostChart.draw(boostData, boostChartOptions);
            }
        }

        function getAnswerRate(data) {
            return (data.answeredCallsCount === 0)
                ? 0
                : data.answeredCallsCount / (data.answeredCallsCount + data.failedCallsCount);
        }

        function getAbandonRate(data) {
            return (data.abandonedCallsCount === 0)
                ? 0
                : data.abandonedCallsCount / (data.abandonedCallsCount + data.handledCallsCount);
        }

        function checkForMaxRowsCount(data) {
            if (data.getNumberOfRows() > maxRows) {
                data.removeRow(0);
            }
        }

        function requestData() {
            if (dataRequesting) {
                $.get('api/boost/getData/' + campaignId, onGetData);
            }
        }

        function requestDataHistory() {
            $.get('api/boost/getDataHistory/' + campaignId, onGetDataHistory);
        }

        function addFailedCall() {
            postData('api/boost/addFailedCall/' + campaignId, $('#manual_calls_count').val());
        }

        function addAnsweredCall() {
            postData('api/boost/addAnsweredCall/' + campaignId, $('#manual_calls_count').val());
        }

        function addAbandonedCall() {
            postData('api/boost/addAbandonedCall/' + campaignId, $('#manual_calls_count').val());
        }

        function addHandledCall() {
            postData('api/boost/addHandledCall/' + campaignId, $('#manual_calls_count').val());
        }

        function postData(url, body) {
            return $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify(body),
                contentType: "application/json; charset=utf-8",
                dataType: "json"
            });
        }

        function togglePause() {
            dataRequesting = !dataRequesting;
            let button = $('#pause_button');
            let buttonColor = (dataRequesting) ? '' : 'lightcoral';
            button.css({background: buttonColor});
        }

        function resetCalculation() {
            postData("api/boost/resetCalculation/" + campaignId);
        }

        function applyEmulation() {
            let request = {
                answeredCallsMin: $('#answered_min').val(),
                answeredCallsMax: $('#answered_max').val(),
                failedCallsMin: $('#failed_min').val(),
                failedCallsMax: $('#failed_max').val(),
                abandonedCallsMin: $('#abandoned_min').val(),
                abandonedCallsMax: $('#abandoned_max').val(),
                handledCallsMin: $('#handled_min').val(),
                handledCallsMax: $('#handled_max').val(),
            }
            postData('api/boost/applyEmulation/' + campaignId, request);
        }

        function stopEmulation() {
            $.post('api/boost/stopEmulation/' + campaignId);
        }

        function openEmulationDialog() {
            $.get('api/boost/getEmulationParams/' + campaignId).then(result => {
                $('#answered_min').val((result.answeredCallsMin) ? result.answeredCallsMin : 0);
                $('#answered_max').val((result.answeredCallsMax) ? result.answeredCallsMax : 0);
                $('#failed_min').val((result.failedCallsMin) ? result.failedCallsMin : 0);
                $('#failed_max').val((result.failedCallsMax) ? result.failedCallsMax : 0);
                $('#abandoned_min').val((result.abandonedCallsMin) ? result.abandonedCallsMin : 0);
                $('#abandoned_max').val((result.abandonedCallsMax) ? result.abandonedCallsMax : 0);
                $('#handled_min').val((result.handledCallsMin) ? result.handledCallsMin : 0);
                $('#handled_max').val((result.handledCallsMax) ? result.handledCallsMax : 0);
                $('#emulation_dialog').dialog('open');
            });
        }

        function applyConfig() {
            let request = {

                recalculationInitialDelaySec: $('#recalculation_initial_delay').val(),
                recalculationIntervalSec: $('#recalculation_interval').val(),
                failedCallMaxConsiderationTimeSec: $('#failed_call_max_consideration_time').val(),
                stoppedCampaignBoostPreservationTimeSec: $('#stopped_campaign_boost_preservation_time').val(),
                recalculationIntervalMinCallsCount: $('#recalculation_interval_min_calls_count').val(),
                emergencyRecalculationAbandonedCallsThreshold: $('#emergency_recalculation_abandoned_calls_threshold').val(),

                defaultBoostValue: $('#default_boost_value').val(),
                minBoostValue: $('#min_boost_value').val(),
                maxBoostValue: $('#max_boost_value').val(),

                abandonRateBoostDecreaseDeltaFactor: $('#abandon_rate_boost_decrease_delta_factor').val(),
                answerRateBoostDecreaseDeltaFactor: $('#answer_rate_boost_decrease_delta_factor').val(),
                answerRateBoostIncreaseDeltaFactor: $('#answer_rate_boost_increase_delta_factor').val(),
                zeroAnswerRateTargetBoostMultiplier: $('#zero_answer_rate_target_boost_multiplier').val()
            };
            postData('api/boost/setConfig/', request);
            $('#config_dialog').dialog('close');
        }

        function openConfigDialog() {
            $.get('api/boost/getConfig/').then(result => {

                $('#recalculation_initial_delay').val(result.recalculationInitialDelaySec);
                $('#recalculation_interval').val(result.recalculationIntervalSec);
                $('#failed_call_max_consideration_time').val(result.failedCallMaxConsiderationTimeSec);
                $('#stopped_campaign_boost_preservation_time').val(result.stoppedCampaignBoostPreservationTimeSec);
                $('#recalculation_interval_min_calls_count').val(result.recalculationIntervalMinCallsCount);
                $('#emergency_recalculation_abandoned_calls_threshold').val(result.emergencyRecalculationAbandonedCallsThreshold);

                $('#default_boost_value').val(result.defaultBoostValue);
                $('#min_boost_value').val(result.minBoostValue);
                $('#max_boost_value').val(result.maxBoostValue);

                $('#abandon_rate_boost_decrease_delta_factor').val(result.abandonRateBoostDecreaseDeltaFactor);
                $('#answer_rate_boost_decrease_delta_factor').val(result.answerRateBoostDecreaseDeltaFactor);
                $('#answer_rate_boost_increase_delta_factor').val(result.answerRateBoostIncreaseDeltaFactor);
                $('#zero_answer_rate_target_boost_multiplier').val(result.zeroAnswerRateTargetBoostMultiplier);

                $('#config_dialog').dialog('open');
            });
        }

        $( function() {
            $('#emulation_dialog').dialog({
                autoOpen: false,
                title: 'Events Emulation',
                width: 390,
                resizable: false
            });
            $('#emulation_dialog fieldset').controlgroup();
            $('#config_dialog fieldset').controlgroup();
            $('#config_dialog').dialog({
                autoOpen: false,
                title: 'Global Configuration',
                width: 410,
                resizable: false
            });
        });

        google.charts.load('current', {'packages': ['corechart']});
        google.charts.setOnLoadCallback(() => {
            console.info('Chart library loaded');
            failedCallsData = new google.visualization.DataTable();
            failedCallsData.addColumn('datetime', 'Time');
            failedCallsData.addColumn('number', 'Answered Calls');
            failedCallsData.addColumn('number', 'Failed Calls');
            failedCallsData.addColumn('number', 'Answer Rate');
            abandonedCallsData = new google.visualization.DataTable();
            abandonedCallsData.addColumn('datetime', 'Time');
            abandonedCallsData.addColumn('number', 'Abandoned Calls');
            abandonedCallsData.addColumn('number', 'Handled Calls');
            abandonedCallsData.addColumn('number', 'Abandon Rate');
            boostData = new google.visualization.DataTable();
            boostData.addColumn('datetime', 'Time');
            boostData.addColumn('number', 'Boost');
            createFailedCallsChart();
            createAbandonedCallsChart();
            createBoostChart();
            requestDataHistory();
            setInterval(requestData, refreshRate || 5000);
        });

    </script>


</head>
<body>
<b>Campaign: ${param.campaignId}</b>
<button id="pause_button" class="ui-button" onclick="togglePause()">Pause Collecting</button>
<button id="reset_button" class="ui-button" onclick="resetCalculation()">Reset Boost</button>
<button id="emulation_button" class="ui-button" onclick="openEmulationDialog()">Events Emulation</button>
<button id="config_button" class="ui-button" onclick="openConfigDialog()">Global Configuration</button>
<hr/>
<div id="failed_calls_chart" class="chart"></div>
<div id="abandoned_calls_chart" class="chart"></div>
<div id="boost_chart" class="chart"></div>

<!-- Emulation dialog -->

<div id="emulation_dialog" class="modal">
    <fieldset>
        <legend>Auto emulation (each 1 second)</legend>
        <table>
            <tr>
                <td><p>Answered Calls</p></td>
                <td>
                    <label for="answered_min">Min </label><input id="answered_min" type="number" min="0" max="1000" value="0"/>
                    <label for="answered_max">Max </label><input id="answered_max" type="number" min="0" max="1000" value="0"/>
                </td>
            </tr>
            <tr>
                <td><p>Failed Calls</p></td>
                <td>
                    <label for="failed_min">Min </label><input id="failed_min" type="number" min="0" max="1000" value="0"/>
                    <label for="failed_max">Max </label><input id="failed_max" type="number" min="0" max="1000" value="0"/>
                </td>
            </tr>
            <tr>
                <td><p>Abandoned Calls</p></td>
                <td>
                    <label for="abandoned_min">Min </label><input id="abandoned_min" type="number" min="0" max="1000" value="0"/>
                    <label for="abandoned_max">Max </label><input id="abandoned_max" type="number" min="0" max="1000" value="0"/>
                </td>
            </tr>
            <tr>
                <td><p>Handled Calls</p></td>
                <td>
                    <label for="handled_min">Min </label><input id="handled_min" type="number" min="0" max="1000" value="0"/>
                    <label for="handled_max">Max </label><input id="handled_max" type="number" min="0" max="1000" value="0"/>
                </td>
            </tr>
        </table>
        <button onclick="applyEmulation()">Apply</button>
        <button onclick="stopEmulation()">Stop</button>
    </fieldset>
    <fieldset>
        <legend>Manual emulation</legend>
        <div><label for="manual_calls_count">Calls count </label><input id="manual_calls_count" type="number" min="0" max="1000" value="0"/></div>
        <button style="width: 156px" onclick="addFailedCall()">Add Failed Calls</button>
        <button style="width: 156px" onclick="addAnsweredCall()">Add Answered Calls</button>
        <button style="width: 156px" onclick="addAbandonedCall()">Add Abandoned Calls</button>
        <button style="width: 156px" onclick="addHandledCall()">Add Handled Calls</button>
    </fieldset>
</div>

<!-- Configuration dialog -->

<div id="config_dialog" class="modal">
    <fieldset>
        <table>
            <tr>
                <td><label for="recalculation_initial_delay">Recalculation Initial Delay (s) </label></td>
                <td><input id="recalculation_initial_delay" type="number" min="0" max="10000" value="0"/></td>
            </tr><tr>
                <td><label for="recalculation_interval">Recalculation Interval (s) </label></td>
                <td><input id="recalculation_interval" type="number" min="0" max="10000" value="0"/></td>
            </tr><tr>
                <td><label for="failed_call_max_consideration_time">Failed Call Max Consideration Time (s) </label></td>
                <td><input id="failed_call_max_consideration_time" type="number" min="0" max="10000" value="0"/></td>
            </tr><tr>
                <td><label for="stopped_campaign_boost_preservation_time">Stopped Campaign Boost Preservation Time (s) </label></td>
                <td><input id="stopped_campaign_boost_preservation_time" type="number" min="0" max="10000" value="0"/></td>
            </tr><tr>
                <td><label for="recalculation_interval_min_calls_count">Recalculation Interval Min Calls Count </label></td>
                <td><input id="recalculation_interval_min_calls_count" type="number" min="0" max="10000" value="0"/></td>
            </tr><tr>
                <td><label for="emergency_recalculation_abandoned_calls_threshold">Emergency Recalculation Abandoned Calls Threshold </label></td>
                <td><input id="emergency_recalculation_abandoned_calls_threshold" type="number" min="0" max="10000" value="0"/></td>
            </tr>
        </table>
    </fieldset>
    <fieldset>
        <table>
            <tr>
                <td><label for="default_boost_value">Default Boost Value </label></td>
                <td><input id="default_boost_value" type="number" min="0" max="10000" value="0" step="0.01"/></td>
            </tr><tr>
                <td><label for="min_boost_value">Minimum Boost Value </label></td>
                <td><input id="min_boost_value" type="number" min="0" max="10000" value="0" step="0.01"/></td>
            </tr><tr>
                <td><label for="max_boost_value">Maximum Boost Value </label></td>
                <td><input id="max_boost_value" type="number" min="0" max="10000" value="0" step="0.01"/></td>
            </tr>
        </table>
    </fieldset>
    <fieldset>
        <table>
            <tr>
                <td><label for="abandon_rate_boost_decrease_delta_factor">Abandon Rate Boost Decrease Delta Factor</label></td>
                <td><input id="abandon_rate_boost_decrease_delta_factor" type="number" min="0" max="1" value="0" step="0.01"/></td>
            </tr><tr>
                <td><label for="answer_rate_boost_decrease_delta_factor">Answer Rate Boost Decrease Delta Factor</label></td>
                <td><input id="answer_rate_boost_decrease_delta_factor" type="number" min="0" max="1" value="0" step="0.01"/></td>
            </tr><tr>
                <td><label for="answer_rate_boost_increase_delta_factor">Answer Rate Boost Increase Delta Factor</label></td>
                <td><input id="answer_rate_boost_increase_delta_factor" type="number" min="0" max="1" value="0" step="0.01"/></td>
            </tr><tr>
                <td><label for="zero_answer_rate_target_boost_multiplier">Zero Answer Rate Target Boost Multiplier</label></td>
                <td><input id="zero_answer_rate_target_boost_multiplier" type="number" min="0" max="1" value="0" step="0.01"/></td>
            </tr>
        </table>
    </fieldset>
    <div class="button_panel">
    <button class="ui-button" onclick="applyConfig()">OK</button>
    <button class="ui-button" onclick="$('#config_dialog').dialog('close')">Cancel</button>
    </div>
</div>

</body>
</html>
