package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import models.Costs
import models.UserRequestType

object CostsApi {
    suspend fun getCosts(): Costs {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/costs")
        return response.body()
    }

    suspend fun userRequestFee(type: UserRequestType): Long {
        val response = ApiClient.httpClient.get(
            "${ApiConstants.API_BASE_URL}/costs/user-request-fee?type=${type.name}"
        )
        return response.body()
    }
}
