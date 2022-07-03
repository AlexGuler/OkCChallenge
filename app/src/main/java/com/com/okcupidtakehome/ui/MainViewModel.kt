package com.com.okcupidtakehome.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.com.okcupidtakehome.models.Pet
import com.com.okcupidtakehome.repo.Repo
import com.com.okcupidtakehome.repo.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: Repo
) : ViewModel() {

    private val petJobs = mutableSetOf<PetJob>()

    private var petsList = listOf<PetCard>()
    private val _pets = MutableLiveData<List<PetCard>>()
    val pets: LiveData<List<PetCard>> = _pets

    private val _topLikedPets = MutableLiveData<List<PetCard>>()
    val topLikedPets: LiveData<List<PetCard>> = _topLikedPets

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<Boolean>()
    val error: LiveData<Boolean> = _error

    init {
        getPets()
    }

    fun getPets() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            _error.postValue(false)
            val result = try {
                repo.getPets()
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred:", e)
                Result.Error(e)
            }
            _loading.postValue(false)
            when (result) {
                is Result.Success<List<Pet>> -> {
                    petsList = result.data.map { PetCard(pet = it, isLoading = false) }
                    _pets.postValue(petsList)
                    emitTopLikedPets()
                }
                else -> {
                    _error.postValue(true)
                }
            }
        }
    }

    private fun emitTopLikedPets() {
        _topLikedPets.postValue(
            petsList
                .filter { it.pet.liked }
                .sortedByDescending { it.pet.match }
                .take(6)
        )
    }

    fun petSelected(pet: Pet) {
        if (pet.liked) {
            _petSelected(pet)
        } else {
            val job = viewModelScope.launch {
                petsList = petsList.map {
                    if (it.pet.userId == pet.userId) {
                        it.copy(isLoading = true)
                    } else {
                        it
                    }
                }
                _pets.postValue(petsList)
                delay(5000L)
                _petSelected(pet)
            }
            petJobs.add(PetJob(id = pet.userId, job = job))
        }
    }

    private fun _petSelected(pet: Pet) {
        petsList = petsList.map {
            if (it.pet.userId == pet.userId) {
                it.copy(pet = pet.copy(liked = !pet.liked), isLoading = false)
            } else {
                it
            }
        }
        _pets.postValue(petsList)
        petJobs.removeAll { it.id == pet.userId }
        petJobs.forEach {
            Log.d(TAG, "alex: _petSelected: id: ${it.id}, isCancelled: ${it.job.isCancelled}, isActive: ${it.job.isActive}")
        }
        emitTopLikedPets()
    }

    fun onPetCancelled(pet: Pet) {
        Log.d(TAG, "alex: onPetCancelled: cancelled pet: ${pet.userName}")
        petJobs.find { it.id == pet.userId }?.let {
            it.job.cancel()
            petJobs.remove(it)
            Log.d(TAG, "alex: onPetCancelled: ${it.id}, isCancelled: ${it.job.isCancelled}, isActive: ${it.job.isActive}")
            petsList = petsList.map { petCard ->
                if (petCard.pet.userId == pet.userId) {
                    petCard.copy(isLoading = false)
                } else {
                    petCard
                }
            }
            _pets.postValue(petsList)
        }
    }

    data class PetJob(
        val id: String,
        val job: Job
    )

    companion object {
        private const val TAG = "MainViewModel"
    }
}
