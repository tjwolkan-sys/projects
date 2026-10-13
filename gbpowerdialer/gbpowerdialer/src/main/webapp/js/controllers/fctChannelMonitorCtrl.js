/**
 * FCT Kanal Monitörü. FctChannelRegistry snapshot'ını periyodik çeker ve kanal rezervasyonlarını,
 * kapasiteyi, cooldown'ları ve TTL prune sinyalini canlı gösterir.
 *
 * Yanıp sönmeme kuralı: listeler ve lambalar template'te `track by` ile render edilir; periyodik
 * render mevcut DOM node'larını yeniden kullanır, yalnız değişen text/class güncellenir. Bu yüzden
 * controller yeni dizi üretse de lamba animasyonları sıfırlanmaz.
 */
app.controller('fctChannelMonitorCtrl', [ '$scope', '$interval', 'fctChannelFactory',
  function($scope, $interval, fctChannelFactory) {

    var vm = this;

    var LAMP_MAX = 10;
    var SPARKLINE_SAMPLES = 60;
    var SPARKLINE_WIDTH = 220;
    var SPARKLINE_HEIGHT = 56;

    vm.loaded = false;
    vm.loadError = false;
    vm.paused = false;
    vm.autoRefresh = true;
    vm.intervalSeconds = 2;
    vm.intervalOptions = [ 1, 2, 5 ];
    vm.viewMode = 'table';
    vm.search = '';
    vm.typeFilter = '';
    vm.typeOptions = [];
    vm.showAllEvents = false;
    vm.capacityMissing = false;

    vm.snapshot = null;
    vm.entries = [];
    vm.visibleEntries = [];
    vm.visibleEvents = [];
    vm.aggregate = {
      totalCapacity : 0,
      totalInFlight : 0,
      occupancyRate : 0,
      cooldownCount : 0
    };
    vm.kpi = {
      ttlSeconds : 0,
      avgAgeSeconds : 0,
      pruned : 0,
      skipped : 0
    };
    vm.sparkline = {
      points : '',
      area : '',
      width : SPARKLINE_WIDTH,
      height : SPARKLINE_HEIGHT
    };
    vm.clock = new Date();

    var occupancyHistory = [];
    var serverTimeOffsetMs = 0;
    var pollPromise = null;
    var tickPromise = null;

    // --- veri yükleme -------------------------------------------------------

    vm.load = function() {
      fctChannelFactory.getSnapshot().then(function(response) {
        vm.loadError = false;
        vm.loaded = true;
        applySnapshot(response.data);
      }, function() {
        vm.loadError = true;
      });
    };

    function applySnapshot(snapshot) {
      if (!snapshot) {
        return;
      }
      vm.snapshot = snapshot;
      serverTimeOffsetMs = (snapshot.serverTimeMs || 0) - new Date().getTime();

      var entries = snapshot.entries || [];
      var index;
      for (index = 0; index < entries.length; index++) {
        decorateEntry(entries[index], snapshot);
      }
      vm.entries = entries;
      vm.typeOptions = collectTypes(entries);
      updateAggregate(entries);
      updateKpi(snapshot, entries);
      pushOccupancySample(vm.aggregate.occupancyRate);
      decorateEvents(snapshot.recentEvents || []);
      vm.applyFilters();
    }

    // --- entry türetmeleri --------------------------------------------------

    function decorateEntry(entry, snapshot) {
      var inFlight = entry.inFlight || [];
      var capacity = entry.capacity || 0;
      var effectiveCapacity = entry.effectiveCapacity || 0;
      var limit = effectiveCapacity > 0 ? effectiveCapacity : capacity;

      // QSS type alanı virgülle birleşik çok değerli gelir (CT_MANUAL -> Manual, CT_PD_RETRY -> Retry,
      // CT_PD_CALLBACK -> Callback, CT_PD_FRESH -> Dialer First). Badge başına bir token gösterilir,
      // filtre de token bazlı çalışır.
      entry.typeTokens = splitTokens(entry.type);
      // QSS attempts alanı da virgülle birleşiktir ve route'ta hiç AT_ attribute'u yoksa 0..99 ile
      // doldurulur; ham hâliyle basılırsa ~290 karakterlik dizi olur, bu yüzden özetlenir.
      entry.attemptsSummary = summarizeTokens(entry.attempts);
      entry.inFlightCount = inFlight.length;
      entry.limit = limit;
      entry.occupancyPercent = limit > 0 ? Math.round((inFlight.length * 100) / limit) : 0;
      entry.headroomApplied = capacity > 0 && effectiveCapacity > 0 && effectiveCapacity !== capacity;
      entry.cooldownActive = isCooldownActive(entry, snapshot);
      entry.lamps = buildLamps(entry);
      entry.status = buildStatus(entry);
      entry.ageChips = buildAgeChips(entry, snapshot);
      entry.overflowCount = Math.max(0, inFlight.length - 3);
      entry.lastEventLabel = formatClock(entry.lastEventMs);
      entry.lastEventHealthy = !entry.cooldownActive;
      entry.rowClass = entry.cooldownActive ? 'fct-row-cooldown' : '';
    }

    function isCooldownActive(entry, snapshot) {
      var reference = snapshot ? snapshot.serverTimeMs : nowMs();
      return (entry.cooldownUntilMs || 0) > reference;
    }

    function buildLamps(entry) {
      var capacity = entry.capacity || 0;
      var lamps = [];
      if (capacity <= 0) {
        // Kapasite bilinmiyor (QSS alanı gelmiyor): kanal slotu çizilemez, yalnız in-flight gösterilir.
        var unknownIndex;
        for (unknownIndex = 0; unknownIndex < Math.min(entry.inFlightCount, LAMP_MAX); unknownIndex++) {
          lamps.push({
            cssClass : 'fct-lamp fct-lamp-unknown'
          });
        }
        return lamps;
      }
      var channelsPerLamp = Math.ceil(capacity / LAMP_MAX);
      var lampCount = Math.ceil(capacity / channelsPerLamp);
      var litCount = Math.min(lampCount, Math.ceil(entry.inFlightCount / channelsPerLamp));
      var lampIndex;
      for (lampIndex = 0; lampIndex < lampCount; lampIndex++) {
        lamps.push({
          cssClass : lampCssClass(entry, lampIndex < litCount)
        });
      }
      return lamps;
    }

    function lampCssClass(entry, lit) {
      if (entry.cooldownActive) {
        return 'fct-lamp fct-lamp-cooldown';
      }
      if (!lit) {
        return 'fct-lamp fct-lamp-empty';
      }
      if (entry.occupancyPercent >= 100) {
        return 'fct-lamp fct-lamp-full';
      }
      if (entry.occupancyPercent >= 50) {
        return 'fct-lamp fct-lamp-busy';
      }
      return 'fct-lamp fct-lamp-free';
    }

    function buildStatus(entry) {
      if (entry.cooldownActive) {
        return {
          label : 'COOLDOWN',
          cssClass : 'fct-badge fct-badge-danger',
          remaining : cooldownRemaining(entry),
          showRemaining : true
        };
      }
      if (entry.occupancyPercent >= 100) {
        return {
          label : 'FULL',
          cssClass : 'fct-badge fct-badge-danger'
        };
      }
      if (entry.occupancyPercent >= 80) {
        return {
          label : 'HIGH USAGE',
          cssClass : 'fct-badge fct-badge-warning'
        };
      }
      if (entry.occupancyPercent >= 50) {
        return {
          label : 'MEDIUM',
          cssClass : 'fct-badge fct-badge-medium'
        };
      }
      if (entry.inFlightCount > 0) {
        return {
          label : 'HEALTHY',
          cssClass : 'fct-badge fct-badge-success'
        };
      }
      return {
        label : 'IDLE',
        cssClass : 'fct-badge fct-badge-idle'
      };
    }

    function buildAgeChips(entry, snapshot) {
      var inFlight = entry.inFlight || [];
      var sorted = inFlight.slice().sort(function(left, right) {
        return (left.sinceMs || 0) - (right.sinceMs || 0);
      });
      var chips = [];
      var chipIndex;
      for (chipIndex = 0; chipIndex < Math.min(3, sorted.length); chipIndex++) {
        var reservation = sorted[chipIndex];
        chips.push({
          callId : reservation.callId,
          shortCallId : shortCallId(reservation.callId),
          sinceMs : reservation.sinceMs,
          ageSeconds : ageSeconds(reservation.sinceMs),
          stale : isStale(reservation, snapshot)
        });
      }
      return chips;
    }

    function isStale(reservation, snapshot) {
      var staleWarnMs = snapshot && snapshot.staleWarnMs ? snapshot.staleWarnMs : 0;
      if (staleWarnMs <= 0) {
        return false;
      }
      return (nowMs() - (reservation.sinceMs || 0)) > staleWarnMs;
    }

    // --- aggregate / KPI / sparkline ---------------------------------------

    function updateAggregate(entries) {
      var totalCapacity = 0;
      var totalInFlight = 0;
      var cooldownCount = 0;
      var capacityKnownCount = 0;
      var index;
      for (index = 0; index < entries.length; index++) {
        totalCapacity += entries[index].limit || 0;
        totalInFlight += entries[index].inFlightCount || 0;
        if (entries[index].cooldownActive) {
          cooldownCount++;
        }
        if ((entries[index].capacity || 0) > 0) {
          capacityKnownCount++;
        }
      }
      // Hiçbir numarada capacity yoksa gate sessizce pasif demektir (QSS alanı gelmiyor veya
      // qss_route.capacity NULL). Bu, proto alan adı/numarası uyumsuzluğunun da tek görünür işaretidir.
      vm.capacityMissing = entries.length > 0 && capacityKnownCount === 0;
      vm.aggregate.totalCapacity = totalCapacity;
      vm.aggregate.totalInFlight = totalInFlight;
      vm.aggregate.occupancyRate = totalCapacity > 0 ? Math.round((totalInFlight * 1000) / totalCapacity) / 10 : 0;
      vm.aggregate.cooldownCount = cooldownCount;
    }

    function updateKpi(snapshot, entries) {
      var kpi = snapshot.kpi || {};
      var totalAge = 0;
      var totalCount = 0;
      var index;
      var reservationIndex;
      for (index = 0; index < entries.length; index++) {
        var inFlight = entries[index].inFlight || [];
        for (reservationIndex = 0; reservationIndex < inFlight.length; reservationIndex++) {
          totalAge += ageSeconds(inFlight[reservationIndex].sinceMs);
          totalCount++;
        }
      }
      vm.kpi.ttlSeconds = Math.round((snapshot.ttlMs || 0) / 1000);
      vm.kpi.avgAgeSeconds = totalCount > 0 ? Math.round((totalAge * 10) / totalCount) / 10 : 0;
      vm.kpi.pruned = kpi.prunedLast5Min || 0;
      vm.kpi.skipped = kpi.skippedLast5Min || 0;
    }

    function pushOccupancySample(rate) {
      occupancyHistory.push(rate);
      while (occupancyHistory.length > SPARKLINE_SAMPLES) {
        occupancyHistory.shift();
      }
      buildSparkline();
    }

    function buildSparkline() {
      if (occupancyHistory.length < 2) {
        vm.sparkline.points = '';
        vm.sparkline.area = '';
        return;
      }
      var stepX = SPARKLINE_WIDTH / (SPARKLINE_SAMPLES - 1);
      var points = [];
      var index;
      for (index = 0; index < occupancyHistory.length; index++) {
        var x = Math.round(index * stepX * 100) / 100;
        var y = Math.round((SPARKLINE_HEIGHT - (occupancyHistory[index] / 100) * SPARKLINE_HEIGHT) * 100) / 100;
        points.push(x + ',' + y);
      }
      vm.sparkline.points = points.join(' ');
      vm.sparkline.area = 'M0,' + SPARKLINE_HEIGHT + ' L' + points.join(' L') + ' L'
          + points[points.length - 1].split(',')[0] + ',' + SPARKLINE_HEIGHT + ' Z';
    }

    // --- feed --------------------------------------------------------------

    function decorateEvents(events) {
      var decorated = [];
      var index;
      for (index = 0; index < events.length; index++) {
        var event = events[index];
        decorated.push({
          id : event.id,
          ts : event.ts,
          time : formatClock(event.ts),
          kind : event.kind,
          label : eventLabel(event.kind),
          cssClass : eventCssClass(event.kind),
          iconClass : eventIconClass(event.kind),
          phoneNumber : event.phoneNumber,
          callId : event.callId,
          shortCallId : shortCallId(event.callId),
          detail : event.detail
        });
      }
      vm.allEvents = decorated;
      vm.visibleEvents = vm.showAllEvents ? decorated : decorated.slice(0, 15);
    }

    function eventLabel(kind) {
      if (kind === 'MARK_FULL') {
        return 'MARK FULL (CAUSE 34)';
      }
      if (kind === 'SKIP') {
        return 'SKIP (COOLDOWN)';
      }
      if (kind === 'PRUNE') {
        return 'PRUNE (TTL EXPIRED)';
      }
      if (kind === 'REJECT') {
        return 'REJECT (CAPACITY FULL)';
      }
      if (kind === 'REORIGINATE') {
        return 'REORIGINATE (NEXT FCT)';
      }
      if (kind === 'FALLBACK_FIXED_LINE') {
        return 'FALLBACK (FIXED LINE)';
      }
      return kind;
    }

    function eventCssClass(kind) {
      if (kind === 'ACQUIRE') {
        return 'fct-event-acquire';
      }
      if (kind === 'RELEASE') {
        return 'fct-event-release';
      }
      if (kind === 'PRUNE') {
        return 'fct-event-prune';
      }
      if (kind === 'SKIP') {
        return 'fct-event-skip';
      }
      if (kind === 'REORIGINATE' || kind === 'FALLBACK_FIXED_LINE') {
        return 'fct-event-skip';
      }
      return 'fct-event-danger';
    }

    function eventIconClass(kind) {
      if (kind === 'ACQUIRE') {
        return 'fa fa-phone';
      }
      if (kind === 'RELEASE') {
        return 'fa fa-phone-square';
      }
      if (kind === 'PRUNE') {
        return 'fa fa-scissors';
      }
      if (kind === 'SKIP') {
        return 'fa fa-clock-o';
      }
      if (kind === 'MARK_FULL') {
        return 'fa fa-exclamation-circle';
      }
      if (kind === 'REORIGINATE') {
        return 'fa fa-repeat';
      }
      if (kind === 'FALLBACK_FIXED_LINE') {
        return 'fa fa-random';
      }
      return 'fa fa-ban';
    }

    vm.toggleShowAllEvents = function() {
      vm.showAllEvents = !vm.showAllEvents;
      if (vm.allEvents) {
        vm.visibleEvents = vm.showAllEvents ? vm.allEvents : vm.allEvents.slice(0, 15);
      }
    };

    // --- filtreler ---------------------------------------------------------

    vm.applyFilters = function() {
      var search = (vm.search || '').replace(/\s+/g, '').toLowerCase();
      var typeFilter = vm.typeFilter || '';
      var filtered = [];
      var index;
      for (index = 0; index < vm.entries.length; index++) {
        var entry = vm.entries[index];
        var number = (entry.phoneNumber || '').replace(/\s+/g, '').toLowerCase();
        var matchesSearch = search === '' || number.indexOf(search) >= 0;
        // Tip token bazlı eşleşir: QSS "Manual,Retry" gönderdiyse hem Manual hem Retry filtresi tutar.
        var matchesType = typeFilter === '' || (entry.typeTokens || []).indexOf(typeFilter) >= 0;
        if (matchesSearch && matchesType) {
          filtered.push(entry);
        }
      }
      vm.visibleEntries = filtered;
    };

    vm.setViewMode = function(mode) {
      vm.viewMode = mode;
    };

    /**
     * Tip seçenekleri snapshot içeriğinden dinamik kurulur ve virgülle birleşik değerler token'lara
     * ayrılır: QSS "Manual,Retry" gönderdiğinde filtre listesinde Manual ve Retry ayrı ayrı çıkar.
     */
    function collectTypes(entries) {
      var types = [];
      var index;
      var tokenIndex;
      for (index = 0; index < entries.length; index++) {
        var tokens = entries[index].typeTokens || [];
        for (tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++) {
          if (types.indexOf(tokens[tokenIndex]) < 0) {
            types.push(tokens[tokenIndex]);
          }
        }
      }
      types.sort();
      return types;
    }

    function splitTokens(value) {
      if (!value) {
        return [];
      }
      var raw = ('' + value).split(',');
      var tokens = [];
      var index;
      for (index = 0; index < raw.length; index++) {
        var token = raw[index].replace(/^\s+|\s+$/g, '');
        if (token !== '' && tokens.indexOf(token) < 0) {
          tokens.push(token);
        }
      }
      return tokens;
    }

    /** Uzun virgüllü listeyi "ilk..son (adet)" biçiminde özetler. */
    function summarizeTokens(value) {
      var tokens = splitTokens(value);
      if (!tokens.length) {
        return '';
      }
      if (tokens.length <= 3) {
        return tokens.join(', ');
      }
      return tokens[0] + '..' + tokens[tokens.length - 1] + ' (' + tokens.length + ')';
    }

    // --- polling / tick ----------------------------------------------------

    vm.togglePause = function() {
      vm.paused = !vm.paused;
      restartPolling();
    };

    vm.toggleAutoRefresh = function() {
      vm.autoRefresh = !vm.autoRefresh;
      restartPolling();
    };

    vm.changeInterval = function() {
      restartPolling();
    };

    function restartPolling() {
      if (pollPromise) {
        $interval.cancel(pollPromise);
        pollPromise = null;
      }
      if (vm.autoRefresh && !vm.paused) {
        pollPromise = $interval(vm.load, vm.intervalSeconds * 1000);
      }
    }

    /**
     * Saniyelik tick: saat, in-flight yaş sayaçları ve cooldown geri sayımları poll aralığından
     * bağımsız olarak yerinde güncellenir. Sunucu saatiyle local saat farkı düzeltilir.
     */
    function tick() {
      vm.clock = new Date();
      if (vm.paused) {
        return;
      }
      var index;
      var chipIndex;
      for (index = 0; index < vm.entries.length; index++) {
        var entry = vm.entries[index];
        entry.cooldownActive = (entry.cooldownUntilMs || 0) > nowMs();
        entry.status = buildStatus(entry);
        entry.lamps = buildLamps(entry);
        entry.rowClass = entry.cooldownActive ? 'fct-row-cooldown' : '';
        for (chipIndex = 0; chipIndex < entry.ageChips.length; chipIndex++) {
          var chip = entry.ageChips[chipIndex];
          chip.ageSeconds = ageSeconds(chip.sinceMs);
          chip.stale = isStale(chip, vm.snapshot);
        }
      }
    }

    // --- yardımcılar -------------------------------------------------------

    function nowMs() {
      return new Date().getTime() + serverTimeOffsetMs;
    }

    function ageSeconds(sinceMs) {
      if (!sinceMs) {
        return 0;
      }
      return Math.max(0, Math.round((nowMs() - sinceMs) / 1000));
    }

    function cooldownRemaining(entry) {
      var remainingMs = (entry.cooldownUntilMs || 0) - nowMs();
      if (remainingMs <= 0) {
        return '00:00';
      }
      var totalSeconds = Math.ceil(remainingMs / 1000);
      var minutes = Math.floor(totalSeconds / 60);
      var seconds = totalSeconds % 60;
      return pad(minutes) + ':' + pad(seconds);
    }

    function shortCallId(callId) {
      if (!callId) {
        return '';
      }
      return callId.length > 10 ? callId.substring(callId.length - 10) : callId;
    }

    function formatClock(timestampMs) {
      if (!timestampMs) {
        return '';
      }
      var date = new Date(timestampMs);
      return pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
    }

    function pad(value) {
      return value < 10 ? '0' + value : '' + value;
    }

    vm.occupancyClass = function(rate) {
      if (rate >= 85) {
        return 'fct-value-danger';
      }
      if (rate >= 60) {
        return 'fct-value-warning';
      }
      return '';
    };

    vm.barClass = function(percent) {
      if (percent >= 100) {
        return 'fct-bar-fill fct-bar-danger';
      }
      if (percent >= 80) {
        return 'fct-bar-fill fct-bar-warning';
      }
      if (percent >= 50) {
        return 'fct-bar-fill fct-bar-medium';
      }
      return 'fct-bar-fill fct-bar-success';
    };

    // --- lifecycle ---------------------------------------------------------

    vm.load();
    restartPolling();
    tickPromise = $interval(tick, 1000);

    $scope.$on('$destroy', function() {
      if (pollPromise) {
        $interval.cancel(pollPromise);
      }
      if (tickPromise) {
        $interval.cancel(tickPromise);
      }
    });

  } ]);
