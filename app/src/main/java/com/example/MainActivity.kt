package com.example

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Channel(
    val name: String,
    val logoUrl: String,
    val group: String,
    val language: String,
    val category: String,
    val streamUrl: String
)

enum class ChannelStatus {
    UNKNOWN, CHECKING, ONLINE, OFFLINE
}

class IptvViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _channelStatuses = MutableStateFlow<Map<String, ChannelStatus>>(emptyMap())
    val channelStatuses: StateFlow<Map<String, ChannelStatus>> = _channelStatuses.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun checkChannelStatus(url: String) {
        if (_channelStatuses.value.containsKey(url)) return
        
        _channelStatuses.value = _channelStatuses.value.toMutableMap().apply { put(url, ChannelStatus.CHECKING) }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                val isOnline = response.isSuccessful
                response.close()
                _channelStatuses.value = _channelStatuses.value.toMutableMap().apply { put(url, if (isOnline) ChannelStatus.ONLINE else ChannelStatus.OFFLINE) }
            } catch (e: Exception) {
                _channelStatuses.value = _channelStatuses.value.toMutableMap().apply { put(url, ChannelStatus.OFFLINE) }
            }
        }
    }

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _languages = MutableStateFlow<List<String>>(emptyList())
    val languages: StateFlow<List<String>> = _languages.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<String?>(null)
    val selectedLanguage: StateFlow<String?> = _selectedLanguage.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedChannels = fetchChannels("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8")
            _channels.value = loadedChannels
            
            val uniqueGroups = loadedChannels.map { it.group }.distinct().sorted()
            _groups.value = uniqueGroups
            if (uniqueGroups.isNotEmpty()) {
                _selectedGroup.value = uniqueGroups.first()
            }

            val uniqueLanguages = loadedChannels.map { it.language }.filter { it.isNotBlank() && it != "Unknown" }.distinct().sorted()
            _languages.value = uniqueLanguages

            val uniqueCategories = loadedChannels.map { it.category }.distinct().sorted()
            _categories.value = uniqueCategories
            
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        _selectedLanguage.value = null
        _selectedCategory.value = null
        _searchQuery.value = ""
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
        _selectedGroup.value = null
        _selectedCategory.value = null
        _searchQuery.value = ""
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        _selectedGroup.value = null
        _selectedLanguage.value = null
        _searchQuery.value = ""
    }

    fun selectChannel(channel: Channel?) {
        _selectedChannel.value = channel
    }

    private suspend fun fetchChannels(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Channel>()
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val lines = body.lines()
                var currentName = ""
                var currentLogo = ""
                var currentGroup = ""
                var currentLang = ""
                
                for (line in lines) {
                    if (line.startsWith("#EXTINF:")) {
                        val logoRegex = "tvg-logo=\"([^\"]+)\"".toRegex()
                        currentLogo = logoRegex.find(line)?.groupValues?.get(1) ?: ""
                        
                        val groupRegex = "group-title=\"([^\"]+)\"".toRegex()
                        currentGroup = groupRegex.find(line)?.groupValues?.get(1) ?: "Unknown"

                        val langRegex = "tvg-language=\"([^\"]+)\"".toRegex()
                        currentLang = langRegex.find(line)?.groupValues?.get(1) ?: "Unknown"
                        
                        currentName = line.substringAfterLast(",").trim()
                    } else if (line.startsWith("http")) {
                        val streamUrl = line.trim()
                        if (currentName.isNotEmpty()) {
                            val nameLower = currentName.lowercase()
                            val category = when {
                                nameLower.contains("news") || nameLower.contains("cnn") || nameLower.contains("info") || nameLower.contains("politique") || nameLower.contains("journal") || nameLower.contains("24") -> "News & Politics"
                                nameLower.contains("sport") || nameLower.contains("espn") || nameLower.contains("bein") || nameLower.contains("golf") || nameLower.contains("racing") -> "Sports"
                                nameLower.contains("music") || nameLower.contains("mtv") || nameLower.contains("radio") || nameLower.contains("mix") -> "Music"
                                nameLower.contains("kids") || nameLower.contains("cartoon") || nameLower.contains("disney") || nameLower.contains("nickelodeon") -> "Kids"
                                nameLower.contains("movie") || nameLower.contains("film") || nameLower.contains("cinema") || nameLower.contains("hbo") -> "Movies"
                                nameLower.contains("doc") || nameLower.contains("discovery") || nameLower.contains("history") || nameLower.contains("nature") -> "Documentary"
                                else -> "General"
                            }
                            list.add(Channel(currentName, currentLogo, currentGroup, currentLang, category, streamUrl))
                            currentName = ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: IptvViewModel = viewModel()
                val selectedChannel by viewModel.selectedChannel.collectAsState()

                if (selectedChannel != null) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.selectChannel(null) }
                    )
                } else {
                    ChannelListScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(viewModel: IptvViewModel) {
    val channels by viewModel.channels.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }

    val filteredChannels = remember(channels, selectedGroup, selectedLanguage, selectedCategory, searchQuery) {
        if (searchQuery.isNotBlank()) {
            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else if (selectedCategory != null) {
            channels.filter { it.category == selectedCategory }
        } else if (selectedLanguage != null) {
            channels.filter { it.language == selectedLanguage }
        } else {
            channels.filter { it.group == selectedGroup }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.duc_tv_header_logo_1784069229731),
                            contentDescription = "Duc Tv Logo",
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Duc Tv") 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .width(250.dp)
                            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
                        placeholder = { Text("Search...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                // TV-style Side Navigation
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Categories") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Countries") }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text("Languages") }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text("About") }
                        )
                    }
                    
                    if (selectedTabIndex == 3) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.duc_tv_logo_1784068701105),
                                contentDescription = "Duc Tv Logo",
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("About Duc Tv", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Développé par un développeur haïtien surnommé duc.", style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Mentions légales:", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Les flux proviennent de sources publiques sur internet. Nous ne diffusons ni n'hébergeons aucun contenu. Cette application n'est qu'un lecteur M3U de listes publiques, telles que IPTV-org.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                            if (selectedTabIndex == 0) {
                                items(categories) { category ->
                                    GroupItem(
                                        group = category,
                                        isSelected = category == selectedCategory && searchQuery.isBlank(),
                                        onClick = { viewModel.selectCategory(category) }
                                    )
                                }
                            } else if (selectedTabIndex == 1) {
                                items(groups) { group ->
                                    GroupItem(
                                        group = group,
                                        isSelected = group == selectedGroup && searchQuery.isBlank(),
                                        onClick = { viewModel.selectGroup(group) }
                                    )
                                }
                            } else {
                                items(languages) { language ->
                                    GroupItem(
                                        group = language,
                                        isSelected = language == selectedLanguage && searchQuery.isBlank(),
                                        onClick = { viewModel.selectLanguage(language) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Channel List
                if (selectedTabIndex != 3) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(2.5f)
                            .fillMaxHeight()
                    ) {
                        if (filteredChannels.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No channels found.", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            items(filteredChannels, key = { it.streamUrl }) { channel ->
                                ChannelItem(channel = channel, viewModel = viewModel, onClick = { viewModel.selectChannel(channel) })
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(2.5f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("Duc Tv", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupItem(group: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = group,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected || isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChannelItem(channel: Channel, viewModel: IptvViewModel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val statuses by viewModel.channelStatuses.collectAsState()
    val status = statuses[channel.streamUrl] ?: ChannelStatus.UNKNOWN

    LaunchedEffect(channel.streamUrl) {
        viewModel.checkChannelStatus(channel.streamUrl)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(end = 16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Status Indicator
            val statusColor = when (status) {
                ChannelStatus.ONLINE -> androidx.compose.ui.graphics.Color.Green
                ChannelStatus.OFFLINE -> androidx.compose.ui.graphics.Color.Red
                ChannelStatus.CHECKING -> androidx.compose.ui.graphics.Color.Gray
                ChannelStatus.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
            }
            val statusText = when (status) {
                ChannelStatus.ONLINE -> "Open"
                ChannelStatus.OFFLINE -> "Off"
                ChannelStatus.CHECKING -> "..."
                ChannelStatus.UNKNOWN -> ""
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(statusColor)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: IptvViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val channel = viewModel.selectedChannel.collectAsState().value ?: return
    val channels = viewModel.channels.collectAsState().value
    val selectedGroup = viewModel.selectedGroup.collectAsState().value
    val selectedLanguage = viewModel.selectedLanguage.collectAsState().value
    val selectedCategory = viewModel.selectedCategory.collectAsState().value
    
    val groupChannels = remember(channels, selectedGroup, selectedLanguage, selectedCategory) { 
        if (selectedCategory != null) {
            channels.filter { it.category == selectedCategory }
        } else if (selectedLanguage != null) {
            channels.filter { it.language == selectedLanguage }
        } else {
            channels.filter { it.group == selectedGroup }
        }
    }

    var showChannelList by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    LaunchedEffect(channel.streamUrl) {
        val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showChannelList = !showChannelList }) {
                        Icon(Icons.Default.List, contentDescription = "Channels")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            )
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            player = exoPlayer
                            keepScreenOn = true
                            useController = true
                            setShowSubtitleButton(true)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Filigrane (Watermark)
                Text(
                    text = "Duc Tv",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
                )
            }
            if (showChannelList) {
                LazyColumn(
                    modifier = Modifier
                        .width(250.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    item {
                        PaddingValues(16.dp)
                        Text(
                            text = "Channels in ${selectedCategory ?: selectedLanguage ?: selectedGroup ?: "List"}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(groupChannels, key = { it.streamUrl }) { ch ->
                        ChannelItem(channel = ch, viewModel = viewModel, onClick = { viewModel.selectChannel(ch) })
                    }
                }
            }
        }
    }
}

