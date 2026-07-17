package com.example.iotcontroller.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("/scan")
    suspend fun scanNetworks(): List<WifiNetwork>

    @POST("/connect")
    suspend fun connectToWifi(@Body request: ConnectRequest): ConnectResponse

    @GET("/status")
    suspend fun getStatus(): EspStatus

    @POST("/restart")
    suspend fun restartDevice(): ConnectResponse

    @GET("/schedule/list")
    suspend fun getSchedules(): List<ScheduleItem>

    @POST("/schedule/add")
    suspend fun addSchedule(@Body request: AddScheduleRequest): AddScheduleResponse

    @POST("/schedule/delete")
    suspend fun deleteSchedule(@Body request: DeleteScheduleRequest): ConnectResponse
}
